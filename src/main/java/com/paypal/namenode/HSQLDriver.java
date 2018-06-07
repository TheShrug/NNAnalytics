/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.paypal.namenode;

import com.google.gson.Gson;
import com.paypal.security.SecurityConfiguration;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HSQLDriver {

  public static final Logger LOG = LoggerFactory.getLogger(HSQLDriver.class.getName());

  private Connection con = null;

  public void dropConnection() throws SQLException {
    if (con != null) {
      con.close();
      con = null;
    }
  }

  public void startDatabase(SecurityConfiguration conf) throws SQLException {
    try {
      Class.forName("org.hsqldb.jdbc.JDBCDriver");
    } catch (ClassNotFoundException e) {
      LOG.info("Failed to register JDBCDriver for HSQLDB.");
    }
    String user = conf.getHistoricalUsername();
    String pass = conf.getHistoricalPassword();
    con = DriverManager.getConnection("jdbc:hsqldb:file:/usr/local/nn-analytics/db/db", user, pass);
  }

  public void createTable() throws SQLException {
    String sqlCreate =
        "CREATE CACHED TABLE IF NOT EXISTS HISTORY"
            + "  (ID BIGINT GENERATED BY DEFAULT AS IDENTITY (START WITH 1, INCREMENT BY 1) NOT NULL PRIMARY KEY,"
            + "   STDATE TIMESTAMP NOT NULL,"
            + "   SYS_DATE DATE NOT NULL,"
            + "   EMPTYDIRS BIGINT,"
            + "   TINYFILES BIGINT,"
            + "   EMPTYFILES BIGINT,"
            + "   SMALLFILES BIGINT,"
            + "   DISKSPACE BIGINT,"
            + "   NUMFILES BIGINT,"
            + "   NUMDIRS BIGINT,"
            + "   USER VARCHAR(100) NOT NULL)";
    try (PreparedStatement preparedStatement = con.prepareStatement(sqlCreate)) {
      preparedStatement.execute();
    }

    String columnCheck =
        "SELECT COUNT(*) AS \"Count\" FROM INFORMATION_SCHEMA.SYSTEM_COLUMNS "
            + "WHERE TABLE_NAME = 'HISTORY' and COLUMN_NAME = 'MEDIUMFILES'";
    boolean columnExists = false;
    try (PreparedStatement colCheckStatement = con.prepareStatement(columnCheck)) {
      try (ResultSet resultSet = colCheckStatement.executeQuery()) {
        while (resultSet.next()) {
          columnExists = (resultSet.getInt(1) != 0);
        }
      }
    }

    if (!columnExists) {
      LOG.info("Adding MediumFiles to History embedded DB table.");
      String addColumn = "ALTER TABLE HISTORY ADD MEDIUMFILES BIGINT";
      try (PreparedStatement columnStatement = con.prepareStatement(addColumn)) {
        columnStatement.execute();
      }
    }
    con.commit();
  }

  public void logHistoryPerUser(
      Map<String, Long> cachedValues, Map<String, Map<String, Long>> cachedMaps, Set<String> users)
      throws SQLException {
    writeHistory(cachedValues, "");
    for (String user : users) {
      // For each user make an entry in database
      Map<String, Long> userData = new HashMap<>();
      userData.put(
          "emptyFiles",
          cachedMaps
              .getOrDefault("emptyFilesUsers", Collections.emptyMap())
              .getOrDefault(user, 0L));
      userData.put(
          "emptyDirs",
          cachedMaps.getOrDefault("emptyDirsUsers", Collections.emptyMap()).getOrDefault(user, 0L));
      userData.put(
          "tinyFiles",
          cachedMaps.getOrDefault("tinyFilesUsers", Collections.emptyMap()).getOrDefault(user, 0L));
      userData.put(
          "smallFiles",
          cachedMaps
              .getOrDefault("smallFilesUsers", Collections.emptyMap())
              .getOrDefault(user, 0L));
      userData.put(
          "diskspace",
          cachedMaps.getOrDefault("diskspaceUsers", Collections.emptyMap()).getOrDefault(user, 0L));
      userData.put(
          "mediumFiles",
          cachedMaps
              .getOrDefault("mediumFilesUsers", Collections.emptyMap())
              .getOrDefault(user, 0L));
      userData.put(
          "numFiles",
          cachedMaps.getOrDefault("numFilesUsers", Collections.emptyMap()).getOrDefault(user, 0L));
      userData.put(
          "numDirs",
          cachedMaps.getOrDefault("numDirsUsers", Collections.emptyMap()).getOrDefault(user, 0L));
      writeHistory(userData, user);
    }
  }

  private void writeHistory(Map<String, Long> data, String user) throws SQLException {
    if (con != null) {
      Date today = new java.util.Date();
      java.sql.Date sqlDate = new java.sql.Date(today.getTime());
      Timestamp current = new java.sql.Timestamp(today.getTime());
      String insertSQL =
          "INSERT INTO HISTORY (STDATE,SYS_DATE,EMPTYDIRS,TINYFILES,EMPTYFILES,SMALLFILES,DISKSPACE,NUMFILES,NUMDIRS,USER,MEDIUMFILES) VALUES (?,?,?,?,?,?,?,?,?,?,?)";
      try (PreparedStatement insertStatement = con.prepareStatement(insertSQL)) {
        // Set Arguments
        insertStatement.setObject(1, current);
        insertStatement.setObject(2, sqlDate);
        insertStatement.setObject(3, data.get("emptyDirs"));
        insertStatement.setObject(4, data.get("tinyFiles"));
        insertStatement.setObject(5, data.get("emptyFiles"));
        insertStatement.setObject(6, data.get("smallFiles"));
        insertStatement.setObject(7, data.get("diskspace"));
        insertStatement.setObject(8, data.get("numFiles"));
        insertStatement.setObject(9, data.get("numDirs"));
        insertStatement.setObject(10, user);
        insertStatement.setObject(11, data.get("mediumFiles"));
        insertStatement.execute();
      }
      con.commit();
    }
  }

  private PreparedStatement buildSQLDeleteQuery(int days) throws SQLException {
    String deleteSQL =
        "DELETE FROM HISTORY WHERE STDATE < DATE_SUB(NOW(), INTERVAL " + days + " DAY)";
    return con.prepareStatement(deleteSQL);
  }

  String getAllHistory(String startDate, String endDate, String user)
      throws SQLException, ParseException {
    if (con != null) {
      ResultSet result = null;
      try {
        if (user != null
            && (startDate == null || startDate.length() == 0)
            && (endDate == null || endDate.length() == 0)) {
          try (PreparedStatement selectStatement =
              con.prepareStatement("SELECT * FROM HISTORY WHERE HISTORY.USER = ?")) {
            selectStatement.setString(1, user);
            result = selectStatement.executeQuery();
          }
        } else if (user != null
            && startDate != null
            && startDate.length() > 0
            && endDate != null
            && endDate.length() > 0) {
          try (PreparedStatement upselectStatement =
              con.prepareStatement(
                  "SELECT * FROM HISTORY WHERE HISTORY.SYS_DATE BETWEEN ? AND ? AND HISTORY.USER = ?")) {
            upselectStatement.setObject(1, convertToSQLDate(startDate));
            upselectStatement.setObject(2, convertToSQLDate(endDate));
            upselectStatement.setString(3, user);
            result = upselectStatement.executeQuery();
          }
        } else {
          throw new SQLException(
              "Please define a proper username and starting and ending date range (MM/dd/YYYY) in the URL.");
        }
        return convertResultSetToJson(result);
      } finally {
        if (result != null) {
          result.close();
        }
      }
    }
    throw new SQLException("DB connection is not open.");
  }

  private java.sql.Date convertToSQLDate(String date) throws ParseException {
    SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy");
    Date javaDate = sdf.parse(date);
    Date modDateTime = new Date(javaDate.getTime());
    return new java.sql.Date(modDateTime.getTime());
  }

  private String convertResultSetToJson(ResultSet resultSet) throws SQLException {
    if (resultSet == null) {
      return null;
    }
    List<Map<String, Object>> list = new ArrayList<>();
    ResultSetMetaData metadata = resultSet.getMetaData();
    int numColumns = metadata.getColumnCount();

    while (resultSet.next()) {
      Map<String, Object> map = new HashMap<>();
      for (int i = 1; i <= numColumns; ++i) {
        String columnName = metadata.getColumnName(i);
        map.put(columnName, resultSet.getObject(columnName));
      }
      list.add(map);
    }

    return new Gson().toJson(list);
  }

  /*
   * Unused now. Storing in H2 was too slow. Using MapDB for cached values instead.
   */
  @Deprecated()
  private Map<String, Long> fetchLogins() throws SQLException {
    Map<String, Long> fetched = new HashMap<>();
    try (PreparedStatement selectStatement = con.prepareStatement("SELECT * FROM LOGIN")) {
      try (ResultSet resultSet = selectStatement.executeQuery()) {
        while (resultSet.next()) {
          String user = resultSet.getString("USER");
          Timestamp login = resultSet.getTimestamp("LOGIN");
          fetched.put(user, login.getTime());
        }
      }
    }
    return fetched;
  }

  public void rebuildTable(String table) throws SQLException {
    switch (table) {
      case "LOGIN":
        try (PreparedStatement dropLogin = con.prepareStatement("DROP TABLE LOGIN IF EXISTS")) {
          dropLogin.execute();
        }
        return;
      case "HISTORY":
        try (PreparedStatement dropHistory = con.prepareStatement("DROP TABLE HISTORY IF EXISTS")) {
          dropHistory.execute();
          createTable();
        }
        return;
      default:
        throw new IllegalArgumentException("No such table: " + table + " exists in embedded DB.");
    }
  }

  public void truncateTable(String table, Integer days) throws SQLException {
    if (days == null || days == 0) {
      throw new IllegalArgumentException("No days to keep limit set for truncate.");
    }
    switch (table) {
      case "HISTORY":
        PreparedStatement truncateStatement = buildSQLDeleteQuery(days);
        truncateStatement.execute();
        truncateStatement.close();
        return;
      default:
        throw new IllegalArgumentException(
            "No such truncate for table: " + table + " exists in embedded DB.");
    }
  }
}