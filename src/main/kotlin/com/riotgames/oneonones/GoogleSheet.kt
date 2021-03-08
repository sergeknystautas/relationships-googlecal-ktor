package com.riotgames.oneonones

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.services.calendar.Calendar
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.ValueRange;
import com.google.api.services.sheets.v4.model.Spreadsheet;
import com.google.api.services.sheets.v4.model.SpreadsheetProperties;
import com.google.api.services.sheets.v4.model.UpdateValuesResponse;

fun getGoogleSheetData (rioter: MyRioterInfo):MutableMap<String, MutableList<String>> {
    val sheetId = "1qnIzGcY4jFeLy5qi48wwt_fekRf4lkpQIBuPcY7HcZs"
    val credential = CREDENTIAL_BUILDER.build().setAccessToken(rioter.accessToken).setRefreshToken(rioter.refreshToken)
    // TODO - when token refreshing works, do that
    val service = Sheets.Builder(HTTP_TRANSPORT, JSON_FACTORY, credential)
        .setApplicationName(projectName)
        .build()
    // This should always return 1 calendar
    val values = service.spreadsheets()
        .values()
        .get(sheetId, "Sheet1!A:D")
        .execute()

    val namesCache = mutableMapOf<String, MutableList<String>>()
    if(values == null || values.isEmpty()) {
        println("No data found in spreadsheet $sheetId")
    }else{
        println("Retrieving Data:")
        for(row in values.getValues() as List<List<String>>)
        {
            namesCache[row[0]] = arrayListOf(row[1], row[2], row[3])
        }
    }
    return namesCache
}