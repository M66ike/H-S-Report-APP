package com.gmes.hsreport

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class ReportStore(context: Context) : SQLiteOpenHelper(context, "gmes_hs_reports.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE reports (
                id TEXT PRIMARY KEY,
                project_name TEXT NOT NULL,
                client TEXT NOT NULL,
                visit_date TEXT NOT NULL,
                status TEXT NOT NULL,
                json TEXT NOT NULL,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE people (
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                role TEXT NOT NULL,
                short_name TEXT NOT NULL,
                json TEXT NOT NULL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun saveReport(report: ReportData) {
        val item = report.copy(updatedAt = System.currentTimeMillis())
        val values = ContentValues().apply {
            put("id", item.id)
            put("project_name", item.projectName)
            put("client", item.client)
            put("visit_date", item.visitDate)
            put("status", item.status)
            put("json", item.toJson())
            put("updated_at", item.updatedAt)
        }
        writableDatabase.insertWithOnConflict("reports", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun getReport(id: String): ReportData? {
        readableDatabase.query(
            "reports", arrayOf("json"), "id=?", arrayOf(id), null, null, null
        ).use { cursor ->
            return if (cursor.moveToFirst()) ReportData.fromJson(cursor.getString(0)) else null
        }
    }

    fun listReports(): List<ReportData> {
        val result = mutableListOf<ReportData>()
        readableDatabase.query(
            "reports", arrayOf("json"), null, null, null, null, "updated_at DESC"
        ).use { cursor ->
            while (cursor.moveToNext()) result += ReportData.fromJson(cursor.getString(0))
        }
        return result
    }

    fun deleteReport(id: String) {
        writableDatabase.delete("reports", "id=?", arrayOf(id))
    }

    fun savePerson(person: Person) {
        val values = ContentValues().apply {
            put("id", person.id)
            put("name", person.name)
            put("role", person.role)
            put("short_name", person.shortName)
            put("json", personToJson(person))
        }
        writableDatabase.insertWithOnConflict("people", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun listPeople(): List<Person> {
        val result = mutableListOf<Person>()
        readableDatabase.query(
            "people", arrayOf("json"), null, null, null, null, "name COLLATE NOCASE"
        ).use { cursor ->
            while (cursor.moveToNext()) result += personFromJson(cursor.getString(0))
        }
        return result
    }

    fun deletePerson(id: String) {
        writableDatabase.delete("people", "id=?", arrayOf(id))
    }
}
