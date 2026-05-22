package com.miktuga.store

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.PopupMenu
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import org.json.JSONArray

class CatalogActivity : AppCompatActivity() {

    private enum class Filter { ALL, INSTALLED, AVAILABLE, PLANNED }
    private enum class Sort(val label: String) {
        DEFAULT("По умолчанию"),
        NAME_ASC("По имени (А-Я)"),
        NAME_DESC("По имени (Я-А)"),
        STATUS("По статусу (установленные первыми)"),
    }

    private val allItems = mutableListOf<CatalogItem>()
    private val filteredItems = mutableListOf<CatalogItem>()
    private var query: String = ""
    private var currentFilter = Filter.ALL
    private var currentSort = Sort.DEFAULT
    private val searchHistory = mutableListOf<String>()

    private lateinit var prefs: SharedPreferences
    private lateinit var adapter: CatalogAdapter
    private lateinit var searchAdapter: ArrayAdapter<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_catalog)

        prefs = getSharedPreferences("tugastore_prefs", Context.MODE_PRIVATE)
        searchHistory.addAll(loadSearchHistory())
        currentSort = runCatching { Sort.valueOf(prefs.getString("sort", Sort.DEFAULT.name)!!) }
            .getOrDefault(Sort.DEFAULT)

        allItems.addAll(loadCatalogItems())
        adapter = CatalogAdapter()
        findViewById<ListView>(R.id.listCatalog).adapter = adapter

        setupSearch()
        setupChips()
        setupSortButton()

        applyFilter()
        updateSummary()
    }

    override fun onResume() {
        super.onResume()
        applyFilter()
        updateSummary()
    }

    private fun setupSearch() {
        val field = findViewById<AutoCompleteTextView>(R.id.editSearch)
        val clear = findViewById<View>(R.id.buttonClearSearch)

        searchAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, searchHistory)
        field.setAdapter(searchAdapter)

        field.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                query = s?.toString()?.trim() ?: ""
                clear.visibility = if (query.isEmpty()) View.GONE else View.VISIBLE
                applyFilter()
            }
        })

        field.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH && query.isNotEmpty()) {
                saveSearchHistory(query)
                field.dismissDropDown()
                true
            } else false
        }

        clear.setOnClickListener { field.setText("") }
    }

    private fun setupChips() {
        val chipAll = findViewById<TextView>(R.id.chipAll)
        val chipInstalled = findViewById<TextView>(R.id.chipInstalled)
        val chipAvailable = findViewById<TextView>(R.id.chipAvailable)
        val chipPlanned = findViewById<TextView>(R.id.chipPlanned)

        chipAll.setOnClickListener { setFilter(Filter.ALL) }
        chipInstalled.setOnClickListener { setFilter(Filter.INSTALLED) }
        chipAvailable.setOnClickListener { setFilter(Filter.AVAILABLE) }
        chipPlanned.setOnClickListener { setFilter(Filter.PLANNED) }

        refreshChipSelection()
    }

    private fun refreshChipSelection() {
        findViewById<TextView>(R.id.chipAll).isSelected = currentFilter == Filter.ALL
        findViewById<TextView>(R.id.chipInstalled).isSelected = currentFilter == Filter.INSTALLED
        findViewById<TextView>(R.id.chipAvailable).isSelected = currentFilter == Filter.AVAILABLE
        findViewById<TextView>(R.id.chipPlanned).isSelected = currentFilter == Filter.PLANNED
    }

    private fun setFilter(f: Filter) {
        currentFilter = f
        refreshChipSelection()
        applyFilter()
    }

    private fun setupSortButton() {
        findViewById<View>(R.id.buttonSort).setOnClickListener { v ->
            val popup = PopupMenu(this, v)
            for ((i, s) in Sort.values().withIndex()) {
                val prefix = if (s == currentSort) "✓  " else "    "
                popup.menu.add(0, i, i, "$prefix${s.label}")
            }
            popup.setOnMenuItemClickListener { item ->
                currentSort = Sort.values()[item.itemId]
                prefs.edit().putString("sort", currentSort.name).apply()
                applyFilter()
                true
            }
            popup.show()
        }
    }

    private fun applyFilter() {
        filteredItems.clear()

        // Filter chain
        val base = allItems.filter { item ->
            val installed = isPackageInstalled(item.packageName)
            val apkExists = item.resolveApk() != null
            val isPlanned = item.apkPath.isEmpty()
            when (currentFilter) {
                Filter.ALL -> true
                Filter.INSTALLED -> installed
                Filter.AVAILABLE -> !installed && apkExists
                Filter.PLANNED -> !installed && !apkExists && isPlanned
            }
        }

        val searched = if (query.isEmpty()) base else {
            val q = query.lowercase()
            base.filter {
                it.title.lowercase().contains(q) ||
                    it.description.lowercase().contains(q) ||
                    it.packageName.lowercase().contains(q)
            }
        }

        // Sort
        val sorted = when (currentSort) {
            Sort.DEFAULT -> searched
            Sort.NAME_ASC -> searched.sortedBy { it.title.lowercase() }
            Sort.NAME_DESC -> searched.sortedByDescending { it.title.lowercase() }
            Sort.STATUS -> searched.sortedBy { statusRank(it) }
        }

        filteredItems.addAll(sorted)
        adapter.notifyDataSetChanged()

        val isEmpty = filteredItems.isEmpty()
        findViewById<ListView>(R.id.listCatalog).visibility =
            if (isEmpty) View.GONE else View.VISIBLE
        findViewById<View>(R.id.emptySearchState).visibility =
            if (isEmpty) View.VISIBLE else View.GONE
        if (isEmpty) {
            val msg = when {
                query.isNotEmpty() -> "По запросу «$query» ничего не найдено"
                currentFilter == Filter.PLANNED -> "Запланированных приложений нет"
                currentFilter == Filter.AVAILABLE -> "Нет приложений, готовых к установке"
                currentFilter == Filter.INSTALLED -> "Ни одно приложение не установлено"
                else -> "Каталог пуст"
            }
            findViewById<TextView>(R.id.textEmptySearch).text = msg
        }
    }

    private fun statusRank(item: CatalogItem): Int {
        val installed = isPackageInstalled(item.packageName)
        val apkExists = item.resolveApk() != null
        return when {
            installed -> 0
            apkExists -> 1
            item.apkPath.isEmpty() -> 2
            else -> 3
        }
    }

    private fun updateSummary() {
        val installed = allItems.count { isPackageInstalled(it.packageName) }
        val available = allItems.count { !isPackageInstalled(it.packageName) && it.resolveApk() != null }
        findViewById<TextView>(R.id.textCatalogSummary).text =
            "$installed установлено · $available доступно · ${allItems.size} всего"
    }

    private fun saveSearchHistory(q: String) {
        if (q.length < 2) return
        searchHistory.remove(q)
        searchHistory.add(0, q)
        while (searchHistory.size > 8) searchHistory.removeAt(searchHistory.size - 1)

        val json = JSONArray()
        for (s in searchHistory) json.put(s)
        prefs.edit().putString("search_history", json.toString()).apply()

        // Refresh adapter so future suggestions include this
        searchAdapter.clear()
        searchAdapter.addAll(searchHistory)
        searchAdapter.notifyDataSetChanged()
    }

    private fun loadSearchHistory(): List<String> = runCatching {
        val raw = prefs.getString("search_history", null) ?: return emptyList()
        val arr = JSONArray(raw)
        (0 until arr.length()).map { arr.optString(it) }.filter { it.isNotEmpty() }
    }.getOrDefault(emptyList())

    private fun loadCatalogItems(): List<CatalogItem> {
        val json = assets.open("catalog.json").bufferedReader().use { it.readText() }
        val array = JSONArray(json)
        val result = mutableListOf<CatalogItem>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            result += CatalogItem(
                id = obj.optString("id"),
                title = obj.optString("title"),
                description = obj.optString("description"),
                packageName = obj.optString("packageName"),
                version = obj.optString("version"),
                apkPath = obj.optString("apkPath", ""),
            )
        }
        return result
    }

    private fun isPackageInstalled(packageName: String): Boolean = runCatching {
        packageManager.getPackageInfo(packageName, 0)
        true
    }.getOrElse { false }

    inner class CatalogAdapter : BaseAdapter() {
        override fun getCount() = filteredItems.size
        override fun getItem(position: Int): Any = filteredItems[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: LayoutInflater.from(this@CatalogActivity)
                .inflate(R.layout.item_catalog, parent, false)

            val item = filteredItems[position]
            val installed = isPackageInstalled(item.packageName)
            val apkFile = item.resolveApk()
            val apkExists = apkFile != null

            view.findViewById<TextView>(R.id.textTitle).text = item.title
            view.findViewById<TextView>(R.id.textDescription).text = item.description

            val statusView = view.findViewById<TextView>(R.id.textStatus)
            val statusDot = view.findViewById<View>(R.id.statusDot)
            val actionButton = view.findViewById<Button>(R.id.buttonAction)

            when {
                installed -> {
                    statusView.text = "v${item.version} · установлено"
                    statusView.setTextColor(ContextCompat.getColor(this@CatalogActivity, R.color.status_ok))
                    statusDot.setBackgroundResource(R.drawable.status_dot_green)
                    actionButton.text = "ОТКРЫТЬ"
                    actionButton.setBackgroundResource(R.drawable.button_filled_teal)
                    actionButton.isEnabled = true
                    actionButton.setOnClickListener {
                        ApkInstaller.openApp(this@CatalogActivity, item.packageName)
                    }
                }
                apkExists -> {
                    statusView.text = "APK найден · v${item.version}"
                    statusView.setTextColor(ContextCompat.getColor(this@CatalogActivity, R.color.status_warn))
                    statusDot.setBackgroundResource(R.drawable.status_dot_orange)
                    actionButton.text = "УСТАНОВИТЬ"
                    actionButton.setBackgroundResource(R.drawable.button_filled_orange)
                    actionButton.isEnabled = true
                    actionButton.setOnClickListener {
                        ApkInstaller.install(this@CatalogActivity, apkFile!!.absolutePath, item.title)
                    }
                }
                else -> {
                    val isPlanned = item.apkPath.isEmpty()
                    statusView.text = if (isPlanned) "Запланировано" else "APK не найден"
                    statusView.setTextColor(ContextCompat.getColor(this@CatalogActivity, R.color.text_dim))
                    statusDot.setBackgroundResource(R.drawable.status_dot_gray)
                    actionButton.text = "—"
                    actionButton.setBackgroundResource(R.drawable.button_disabled)
                    actionButton.isEnabled = false
                    actionButton.setOnClickListener(null)
                }
            }

            return view
        }
    }
}
