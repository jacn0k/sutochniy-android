package com.dailybudget.app

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var db: DatabaseHelper
    private lateinit var pieChartView: PieChartView
    private var activeDialog: AlertDialog? = null

    private val DONATION_URL = "https://pay.cloudtips.ru/p/b7a00553"

    private val CUSTOM_CATEGORY_TRIGGER = "+ Своя категория..."
    private val defaultCategories = arrayListOf(
        "Свидания",
        "Продукты",
        "Кафе/Рестораны",
        "Транспорт",
        "Жилье/ЖКХ",
        "Покупки",
        "Развлечения",
        "Здоровье",
        "Другое",
        CUSTOM_CATEGORY_TRIGGER
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        db = DatabaseHelper.getInstance(this)
        pieChartView = findViewById(R.id.pieChartView)

        val spinner = findViewById<Spinner>(R.id.spCategory)
        val etCustomCategory = findViewById<EditText>(R.id.etCustomCategory)
        
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, defaultCategories)
        spinner.adapter = adapter

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (defaultCategories[position] == CUSTOM_CATEGORY_TRIGGER) {
                    etCustomCategory.visibility = View.VISIBLE
                    etCustomCategory.requestFocus()
                } else {
                    etCustomCategory.visibility = View.GONE
                    etCustomCategory.text.clear()
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        val etInitBudget = findViewById<EditText>(R.id.etInitBudget)
        val etInitDays = findViewById<EditText>(R.id.etInitDays)
        val cbReset = findViewById<CheckBox>(R.id.cbResetExpenses)

        val (budgetCents, cycleDays, _) = db.getConfig()
        etInitBudget.setText(String.format(Locale.getDefault(), "%.0f", budgetCents / 100.0))
        etInitDays.setText(cycleDays.toString())

        findViewById<Button>(R.id.btnApplyBudget).setOnClickListener {
            val budgetStr = etInitBudget.text.toString().replace(',', '.')
            val budget = budgetStr.toDoubleOrNull()
            val days = etInitDays.text.toString().toIntOrNull()

            if (budget != null && budget > 0 && days != null && days > 0) {
                db.setBudgetConfig(budget, days, cbReset.isChecked)
                cbReset.isChecked = false
                updateUI()
                BudgetWidgetProvider.updateAllWidgets(this)
                Toast.makeText(this, "Бюджет успешно обновлен!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Укажите корректную сумму и дни", Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<Button>(R.id.btnAddExpense).setOnClickListener {
            val amountStr = findViewById<EditText>(R.id.etAmount).text.toString().replace(',', '.')
            val amount = amountStr.toDoubleOrNull()
            if (amount == null || amount <= 0) {
                Toast.makeText(this, "Введите корректную сумму", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val selectedCategory = spinner.selectedItem.toString()
            val finalCategory = if (selectedCategory == CUSTOM_CATEGORY_TRIGGER) {
                val custom = etCustomCategory.text.toString().trim()
                if (custom.isEmpty()) {
                    Toast.makeText(this, "Введите название своей категории", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                custom
            } else {
                selectedCategory
            }

            db.addExpense(amount, finalCategory)
            findViewById<EditText>(R.id.etAmount).text.clear()
            etCustomCategory.text.clear()
            spinner.setSelection(0)
            
            updateUI()
            BudgetWidgetProvider.updateAllWidgets(this)
        }

        findViewById<Button>(R.id.btnSupportAuthor).setOnClickListener {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(DONATION_URL))
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "Не удалось открыть браузер", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateUI()
        BudgetWidgetProvider.updateAllWidgets(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        activeDialog?.dismiss()
        activeDialog = null
    }

    private fun updateUI() {
        val res = db.calculateCurrentBudget()
        val (budgetCents, _, _) = db.getConfig()
        val totalBudget = budgetCents / 100.0
        val totalSpent = totalBudget - res.remainingBudgetRubles

        val tvDaily = findViewById<TextView>(R.id.tvDailyAvailable)
        val roundedDaily = res.roundedDailyLimit

        if (res.isCycleEnded) {
            tvDaily.text = "0 ₽"
            tvDaily.setTextColor(Color.GRAY)
        } else {
            tvDaily.text = String.format(Locale.getDefault(), "%,d ₽", roundedDaily)
            if (roundedDaily < 0) {
                tvDaily.setTextColor(Color.parseColor("#C62828"))
            } else {
                tvDaily.setTextColor(Color.parseColor("#2E7D32"))
            }
        }

        val periodText = if (res.isCycleEnded) {
            "Период завершен! Начните новый цикл."
        } else {
            String.format(
                Locale.getDefault(),
                "Осталось дней: %d | Бюджет: %,.0f ₽ | Потрачено: %,.0f ₽",
                res.remainingDays, totalBudget, totalSpent
            )
        }
        findViewById<TextView>(R.id.tvPeriodSummary).text = periodText

        renderCategoriesAndChart()
        renderHistoryList()
    }

    private fun renderCategoriesAndChart() {
        val stats = db.getCategoryStatistics()
        pieChartView.setData(stats)

        val container = findViewById<LinearLayout>(R.id.layoutCategoryStats)
        container.removeAllViews()

        if (stats.isEmpty()) {
            val tv = TextView(this).apply {
                text = "Расходов в текущем периоде пока нет"
                setTextColor(Color.GRAY)
                textAlignment = View.TEXT_ALIGNMENT_CENTER
            }
            container.addView(tv)
            return
        }

        for ((index, item) in stats.withIndex()) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 10, 0, 10)
            }

            val colorMark = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(28, 28).apply {
                    setMargins(0, 8, 16, 0)
                }
                val shape = GradientDrawable().apply {
                    cornerRadius = 14f
                    setColor(pieChartView.categoryColors[index % pieChartView.categoryColors.size])
                }
                background = shape
            }

            val tvTitle = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                text = item.category
                textSize = 14f
                setTextColor(Color.BLACK)
            }

            val tvValue = TextView(this).apply {
                text = String.format(Locale.getDefault(), "%,.2f ₽ (%.1f%%)", item.total, item.percentage)
                textSize = 14f
                setTextColor(Color.DKGRAY)
            }

            row.addView(colorMark)
            row.addView(tvTitle)
            row.addView(tvValue)
            container.addView(row)
        }
    }

    private fun renderHistoryList() {
        val container = findViewById<LinearLayout>(R.id.layoutHistoryList)
        container.removeAllViews()

        val expenses = db.getAllExpenses()
        if (expenses.isEmpty()) {
            val tv = TextView(this).apply {
                text = "История пока пуста"
                setTextColor(Color.GRAY)
                setPadding(0, 16, 0, 16)
                textAlignment = View.TEXT_ALIGNMENT_CENTER
            }
            container.addView(tv)
            return
        }

        for (item in expenses) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(8, 14, 8, 14)
                isClickable = true
                isFocusable = true
                setBackgroundResource(android.R.drawable.list_selector_background)
                setOnClickListener { showEditExpenseDialog(item) }
            }

            val leftCol = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val tvCat = TextView(this).apply {
                text = item.category
                textSize = 15f
                setTextColor(Color.BLACK)
            }

            val tvDate = TextView(this).apply {
                text = item.dateTime
                textSize = 11f
                setTextColor(Color.parseColor("#757575"))
            }

            leftCol.addView(tvCat)
            leftCol.addView(tvDate)

            val tvAmount = TextView(this).apply {
                text = String.format(Locale.getDefault(), "- %,.2f ₽", item.amount)
                textSize = 15f
                setTextColor(Color.parseColor("#D32F2F"))
                textAlignment = View.TEXT_ALIGNMENT_VIEW_END
            }

            row.addView(leftCol)
            row.addView(tvAmount)
            container.addView(row)

            val divider = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
                setBackgroundColor(Color.parseColor("#EEEEEE"))
            }
            container.addView(divider)
        }
    }

    private fun showEditExpenseDialog(expense: Expense) {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 20, 50, 10)
        }

        val etAmount = EditText(this).apply {
            hint = "Сумма (₽)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(if (expense.amount % 1.0 == 0.0) String.format(Locale.US, "%.0f", expense.amount) else String.format(Locale.US, "%.2f", expense.amount))
            setTextColor(Color.BLACK)
        }

        val etCatEdit = EditText(this).apply {
            hint = "Категория"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setText(expense.category)
            setTextColor(Color.BLACK)
        }

        val tvTimeInfo = TextView(this).apply {
            text = "Дата и время: ${expense.dateTime}"
            setTextColor(Color.GRAY)
            textSize = 12f
            setPadding(0, 10, 0, 10)
        }

        layout.addView(tvTimeInfo)
        layout.addView(TextView(this).apply { text = "Сумма:" })
        layout.addView(etAmount)
        layout.addView(TextView(this).apply { text = "Категория:" })
        layout.addView(etCatEdit)

        activeDialog = AlertDialog.Builder(this)
            .setTitle("Редактировать расход")
            .setView(layout)
            .setPositiveButton("Сохранить") { _, _ ->
                val amountStr = etAmount.text.toString().replace(',', '.')
                val newAmount = amountStr.toDoubleOrNull()
                val newCategory = etCatEdit.text.toString().trim()
                if (newAmount != null && newAmount > 0 && newCategory.isNotEmpty()) {
                    db.updateExpense(expense.id, newAmount, newCategory)
                    updateUI()
                    BudgetWidgetProvider.updateAllWidgets(this)
                    Toast.makeText(this, "Расход обновлен", Toast.LENGTH_SHORT).show()
                }
            }
            .setNeutralButton("Удалить") { _, _ ->
                db.deleteExpense(expense.id)
                updateUI()
                BudgetWidgetProvider.updateAllWidgets(this)
                Toast.makeText(this, "Расход удален", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Отмена", null)
            .create()

        activeDialog?.show()
    }
}
