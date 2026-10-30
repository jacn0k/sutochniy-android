package com.dailybudget.app

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToLong

data class Expense(
    val id: Long,
    val amount: Double,
    val category: String,
    val dateTime: String
)

data class CategoryStat(
    val category: String,
    val total: Double,
    val percentage: Float
)

class DatabaseHelper private constructor(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.enableWriteAheadLogging()
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS config (
                key TEXT PRIMARY KEY,
                value TEXT
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS expenses (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                amount_cents INTEGER NOT NULL,
                category TEXT NOT NULL,
                date_time TEXT NOT NULL
            )
            """.trimIndent()
        )

        val now = LocalDate.now().toString()
        db.execSQL("INSERT OR IGNORE INTO config (key, value) VALUES ('budget_cents', '10000000')")
        db.execSQL("INSERT OR IGNORE INTO config (key, value) VALUES ('cycle_days', '30')")
        db.execSQL("INSERT OR IGNORE INTO config (key, value) VALUES ('start_date', '$now')")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            try {
                db.execSQL("ALTER TABLE expenses ADD COLUMN amount_cents INTEGER DEFAULT 0")
                db.execSQL("UPDATE expenses SET amount_cents = CAST(ROUND(amount * 100) AS INTEGER)")
            } catch (_: Exception) {}

            var oldBudget = 100000.0
            try {
                db.rawQuery("SELECT value FROM config WHERE key = 'monthly_budget'", null).use { cursor ->
                    if (cursor.moveToFirst()) {
                        oldBudget = cursor.getString(0).toDoubleOrNull() ?: 100000.0
                    }
                }
            } catch (_: Exception) {}

            db.execSQL("INSERT OR REPLACE INTO config (key, value) VALUES ('budget_cents', '${(oldBudget * 100).roundToLong()}')")
            db.execSQL("DELETE FROM config WHERE key = 'monthly_budget'")
        }
    }

    fun getConfig(today: LocalDate = LocalDate.now()): Triple<Long, Int, LocalDate> {
        val db = readableDatabase
        var budgetCents = 10000000L
        var cycleDays = 30
        var startDate = today

        db.rawQuery("SELECT key, value FROM config", null).use { cursor ->
            while (cursor.moveToNext()) {
                val key = cursor.getString(0)
                val value = cursor.getString(1)
                when (key) {
                    "budget_cents" -> budgetCents = value.toLongOrNull() ?: 10000000L
                    "monthly_budget" -> budgetCents = ((value.toDoubleOrNull() ?: 100000.0) * 100).roundToLong()
                    "cycle_days" -> cycleDays = value.toIntOrNull() ?: 30
                    "start_date" -> {
                        startDate = try {
                            // Берём только часть YYYY-MM-DD
                            LocalDate.parse(value.substring(0, 10))
                        } catch (e: Exception) {
                            today
                        }
                    }
                }
            }
        }
        return Triple(budgetCents, cycleDays, startDate)
    }

    fun calculateCurrentBudget(today: LocalDate = LocalDate.now()): BudgetCalculationResult {
        val (budgetCents, cycleDays, startDate) = getConfig(today)
        val startDatePrefix = startDate.toString()
        val todayPrefix = today.toString()

        val db = readableDatabase

        // 1. Траты за предыдущие дни цикла: дата >= startDatePrefix, но меньше сегодняшней даты
        var spentBeforeTodayCents = 0L
        db.rawQuery(
            "SELECT COALESCE(SUM(amount_cents), 0) FROM expenses WHERE substr(date_time, 1, 10) >= ? AND substr(date_time, 1, 10) < ?",
            arrayOf(startDatePrefix, todayPrefix)
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                spentBeforeTodayCents = cursor.getLong(0)
            }
        }

        // 2. Траты за СЕГОДНЯ: точное совпадение даты (первые 10 символов 'YYYY-MM-DD')
        var spentTodayCents = 0L
        db.rawQuery(
            "SELECT COALESCE(SUM(amount_cents), 0) FROM expenses WHERE substr(date_time, 1, 10) = ?",
            arrayOf(todayPrefix)
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                spentTodayCents = cursor.getLong(0)
            }
        }

        return BudgetCalculator.calculate(
            totalBudgetCents = budgetCents,
            cycleDays = cycleDays,
            startDate = startDate,
            today = today,
            spentBeforeTodayCents = spentBeforeTodayCents,
            spentTodayCents = spentTodayCents
        )
    }

    fun setBudgetConfig(budgetRubles: Double, days: Int, resetExpenses: Boolean) {
        val db = writableDatabase
        val todayStr = LocalDate.now().toString()
        val budgetCents = (budgetRubles * 100).roundToLong()

        db.beginTransaction()
        try {
            db.execSQL("INSERT OR REPLACE INTO config (key, value) VALUES ('budget_cents', '$budgetCents')")
            db.execSQL("INSERT OR REPLACE INTO config (key, value) VALUES ('cycle_days', '$days')")
            db.execSQL("INSERT OR REPLACE INTO config (key, value) VALUES ('start_date', '$todayStr')")

            if (resetExpenses) {
                db.delete("expenses", null, null)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun addExpense(amountRubles: Double, category: String) {
        val db = writableDatabase
        val now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        val values = ContentValues().apply {
            put("amount_cents", (amountRubles * 100).roundToLong())
            put("category", category)
            put("date_time", now)
        }
        db.insert("expenses", null, values)
    }

    fun updateExpense(id: Long, newAmountRubles: Double, newCategory: String) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("amount_cents", (newAmountRubles * 100).roundToLong())
            put("category", newCategory)
        }
        db.update("expenses", values, "id = ?", arrayOf(id.toString()))
    }

    fun deleteExpense(id: Long) {
        val db = writableDatabase
        db.delete("expenses", "id = ?", arrayOf(id.toString()))
    }

    fun getAllExpenses(): List<Expense> {
        val list = mutableListOf<Expense>()
        val db = readableDatabase
        db.rawQuery("SELECT id, amount_cents, category, date_time FROM expenses ORDER BY id DESC LIMIT 50", null).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(
                    Expense(
                        id = cursor.getLong(0),
                        amount = cursor.getLong(1) / 100.0,
                        category = cursor.getString(2) ?: "Другое",
                        dateTime = cursor.getString(3) ?: ""
                    )
                )
            }
        }
        return list
    }

    fun getCategoryStatistics(): List<CategoryStat> {
        val list = mutableListOf<CategoryStat>()
        val today = LocalDate.now()
        val (_, _, startDate) = getConfig(today)
        val startDatePrefix = startDate.toString()

        val db = readableDatabase

        var totalSpentCents = 0L
        db.rawQuery(
            "SELECT COALESCE(SUM(amount_cents), 0) FROM expenses WHERE substr(date_time, 1, 10) >= ?",
            arrayOf(startDatePrefix)
        ).use {
            if (it.moveToFirst()) totalSpentCents = it.getLong(0)
        }

        if (totalSpentCents <= 0) return emptyList()

        db.rawQuery(
            """
            SELECT category, SUM(amount_cents) as cat_total 
            FROM expenses 
            WHERE substr(date_time, 1, 10) >= ? 
            GROUP BY category 
            ORDER BY cat_total DESC
            """.trimIndent(),
            arrayOf(startDatePrefix)
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val cat = cursor.getString(0) ?: "Другое"
                val catTotalCents = cursor.getLong(1)
                val percentage = (catTotalCents.toFloat() / totalSpentCents.toFloat()) * 100f
                list.add(
                    CategoryStat(
                        category = cat,
                        total = catTotalCents / 100.0,
                        percentage = percentage
                    )
                )
            }
        }
        return list
    }

    companion object {
        private const val DATABASE_NAME = "budget.db"
        private const val DATABASE_VERSION = 2

        @Volatile
        private var instance: DatabaseHelper? = null

        fun getInstance(context: Context): DatabaseHelper {
            return instance ?: synchronized(this) {
                instance ?: DatabaseHelper(context).also { instance = it }
            }
        }
    }
}
