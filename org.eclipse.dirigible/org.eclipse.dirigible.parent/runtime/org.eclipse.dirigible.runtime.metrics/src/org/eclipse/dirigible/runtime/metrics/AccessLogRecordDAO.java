/******************************************************************************* 
 * Copyright (c) 2015 SAP and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0 
 * which accompanies this distribution, and is available at 
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   SAP - initial API and implementation
 *******************************************************************************/

package org.eclipse.dirigible.runtime.metrics;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.naming.NamingException;
import javax.sql.DataSource;

import org.eclipse.dirigible.repository.ext.db.DBUtils;
import org.eclipse.dirigible.repository.logging.Logger;
import org.eclipse.dirigible.runtime.repository.RepositoryFacade;

public class AccessLogRecordDAO {
	
	private static final Logger logger = Logger.getLogger(AccessLogRecordDAO.class);
	
	
	private static final String RESPONSE_TIME = "RESPONSE_TIME";

	private static final String ACCLOG_COUNT = "ACCLOG_COUNT";

	private static final String ACCLOG_RESPONSE_TIME = "ACCLOG_RESPONSE_TIME";

	private static final String ACCLOG_PROJECT = "ACCLOG_PROJECT";

	private static final String ACCLOG_PATTERN = "ACCLOG_PATTERN";

	private static final String ACCLOG_PERIOD = "ACCLOG_PERIOD";

	private static final String ACCLOG_TIMESTAMP = "ACCLOG_TIMESTAMP";

	private static final String ACCLOG_RESPONSE_STATUS = "ACCLOG_RESPONSE_STATUS";

	private static final String ACCLOG_USER_AGENT = "ACCLOG_USER_AGENT";

	private static final String ACCLOG_METHOD = "ACCLOG_METHOD";

	private static final String ACCLOG_SESSION_ID = "ACCLOG_SESSION_ID";

	private static final String ACCLOG_REMOTE_HOST = "ACCLOG_REMOTE_HOST";

	private static final String ACCLOG_REMOTE_USER = "ACCLOG_REMOTE_USER";

	private static final String ACCLOG_REQUEST_URI = "ACCLOG_REQUEST_URI";

	
	private static final String SQL_MAP_REMOVE_OLDER_LOG_RECORDS =
			"/org/eclipse/dirigible/runtime/metrics/sql/remove_older_log_records.sql"; //$NON-NLS-1$
	private static final String SQL_MAP_INSERT_LOG_RECORD =
			"/org/eclipse/dirigible/runtime/metrics/sql/insert_log_record.sql"; //$NON-NLS-1$
	private static final String SQL_MAP_CREATE_TABLE_LOG_RECORDS =
			"/org/eclipse/dirigible/runtime/metrics/sql/create_table_log_records.sql"; //$NON-NLS-1$
	private static final String SQL_MAP_SELECT_COUNT_LOG_RECORDS =
			"/org/eclipse/dirigible/runtime/metrics/sql/select_count_log_records.sql"; //$NON-NLS-1$
	private static final String SQL_MAP_SELECT_ALL_LOG_RECORDS =
			"/org/eclipse/dirigible/runtime/metrics/sql/select_all_log_records.sql"; //$NON-NLS-1$
	private static final String SQL_MAP_SELECT_LAST_BY_PATTERN_LOG_RECORD =
			"/org/eclipse/dirigible/runtime/metrics/sql/select_last_by_pattern_log_record.sql"; //$NON-NLS-1$
	private static final String SQL_MAP_SELECT_LAST_BY_PROJECT_LOG_RECORD =
			"/org/eclipse/dirigible/runtime/metrics/sql/select_last_by_project_log_record.sql"; //$NON-NLS-1$
	private static final String SQL_MAP_SELECT_LAST_BY_URI_LOG_RECORD =
			"/org/eclipse/dirigible/runtime/metrics/sql/select_last_by_uri_log_record.sql"; //$NON-NLS-1$
	private static final String SQL_MAP_SELECT_RT_BY_PATTERN_LOG_RECORD =
			"/org/eclipse/dirigible/runtime/metrics/sql/select_rt_by_pattern_log_record.sql"; //$NON-NLS-1$
	private static final String SQL_MAP_SELECT_RT_BY_PROJECT_LOG_RECORD =
			"/org/eclipse/dirigible/runtime/metrics/sql/select_rt_by_project_log_record.sql"; //$NON-NLS-1$
	private static final String SQL_MAP_SELECT_RT_BY_URI_LOG_RECORD =
			"/org/eclipse/dirigible/runtime/metrics/sql/select_rt_by_uri_log_record.sql"; //$NON-NLS-1$
	private static final String SQL_MAP_SELECT_HITS_BY_URI_LOG_RECORD =
			"/org/eclipse/dirigible/runtime/metrics/sql/select_hits_by_uri_log_record.sql"; //$NON-NLS-1$
	
	public static void insert(AccessLogRecord accessLogRecord) throws SQLException, IOException {
		try {
			checkDB();
			
			DataSource dataSource = RepositoryFacade.getInstance()
					.getDataSource();
			Connection connection = null;
			try {
				connection = dataSource.getConnection();
				
				DBUtils dbUtils = new DBUtils(dataSource);
				String sql = dbUtils.readScript(connection, SQL_MAP_INSERT_LOG_RECORD, AccessLogRecordDAO.class);
				PreparedStatement pstmt = connection.prepareStatement(sql);
				
				int i=0;
				pstmt.setString(++i, accessLogRecord.getRequestUri());
				pstmt.setString(++i, accessLogRecord.getRemoteUser());
				pstmt.setString(++i, accessLogRecord.getRemoteHost());
				pstmt.setString(++i, accessLogRecord.getSessionId());
				pstmt.setString(++i, accessLogRecord.getMethod());
				pstmt.setString(++i, accessLogRecord.getUserAgent());
				pstmt.setInt(++i, accessLogRecord.getResponseStatus());
				pstmt.setTimestamp(++i, new Timestamp(accessLogRecord.getTimestamp().getTime()));
				pstmt.setTimestamp(++i, new Timestamp(accessLogRecord.getPeriod().getTime()));
				pstmt.setString(++i, accessLogRecord.getPattern());
				pstmt.setString(++i, accessLogRecord.getProjectName());
				pstmt.setInt(++i, accessLogRecord.getResponseTime());
				
				pstmt.executeUpdate();
				
			} finally {
				if (connection != null) {
					connection.close();
				}
			}
		} catch (NamingException e) {
			throw new SQLException(e);
		}
	}

	private static  void checkDB() throws NamingException, SQLException, IOException {
		DataSource dataSource = RepositoryFacade.getInstance()
				.getDataSource();
		Connection connection = null;
		try {
			connection = dataSource.getConnection();
			Statement stmt = connection.createStatement();
			DBUtils dbUtils = new DBUtils(dataSource);
			String sqlCount = dbUtils.readScript(connection, SQL_MAP_SELECT_COUNT_LOG_RECORDS, AccessLogRecordDAO.class);

			try {
				stmt.executeQuery(sqlCount);
			} catch (Exception e) {
				logger.error("DGB_ACCESS_LOG does not exist?" + e.getMessage(), e);
				// Create Access Log Table
				String sqlCreate = dbUtils.readScript(connection, SQL_MAP_CREATE_TABLE_LOG_RECORDS, AccessLogRecordDAO.class);
				stmt.executeUpdate(sqlCreate);
			}
		} finally {
			if (connection != null) {
				connection.close();
			}
		}
	}

	public static void cleanupOlderRecords() throws SQLException, IOException {
		try {
			checkDB();
			
			DataSource dataSource = RepositoryFacade.getInstance()
					.getDataSource();
			Connection connection = null;
			try {
				connection = dataSource.getConnection();
				DBUtils dbUtils = new DBUtils(dataSource);
				String sql = dbUtils.readScript(connection, SQL_MAP_REMOVE_OLDER_LOG_RECORDS, AccessLogRecordDAO.class);
				PreparedStatement pstmt = connection.prepareStatement(sql);
				
				GregorianCalendar last = new GregorianCalendar();
				last.add(Calendar.WEEK_OF_YEAR, -1);
				pstmt.setTimestamp(1, new Timestamp(last.getTime().getTime()));
				
				pstmt.executeUpdate();
				
			} finally {
				if (connection != null) {
					connection.close();
				}
			}
		} catch (NamingException e) {
			throw new SQLException(e);
		}
		
	}
	
	public static AccessLogRecord[] getAccessLogRecords() throws SQLException, IOException {
		try {
			checkDB();
			
			DataSource dataSource = RepositoryFacade.getInstance()
					.getDataSource();
			Connection connection = null;
			try {
				connection = dataSource.getConnection();
				DBUtils dbUtils = new DBUtils(dataSource);
				String sql = dbUtils.readScript(connection, SQL_MAP_SELECT_ALL_LOG_RECORDS, AccessLogRecordDAO.class);
				PreparedStatement pstmt = connection.prepareStatement(sql);
				
				List<AccessLogRecord> accessLogRecords = new ArrayList<AccessLogRecord>();
				ResultSet rs = pstmt.executeQuery();
				while (rs.next()) {
					AccessLogRecord accessLogRecord = new AccessLogRecord(
						rs.getString(ACCLOG_REQUEST_URI),
						rs.getString(ACCLOG_REMOTE_USER),
						rs.getString(ACCLOG_REMOTE_HOST),
						rs.getString(ACCLOG_SESSION_ID),
						rs.getString(ACCLOG_METHOD),
						rs.getString(ACCLOG_USER_AGENT),
						rs.getInt(ACCLOG_RESPONSE_STATUS),
						rs.getTimestamp(ACCLOG_TIMESTAMP),
						rs.getTimestamp(ACCLOG_PERIOD),
						rs.getString(ACCLOG_PATTERN),
						rs.getString(ACCLOG_PROJECT),
						rs.getInt(ACCLOG_RESPONSE_TIME)
					);
					accessLogRecords.add(accessLogRecord);
				}
				
				return accessLogRecords.toArray(new AccessLogRecord[]{});
				
			} finally {
				if (connection != null) {
					connection.close();
				}
			}
		} catch (NamingException e) {
			throw new SQLException(e);
		}
	}

	
	public String[][] getLastRecordsByPattern() throws SQLException, IOException {
		return getLastRecords(SQL_MAP_SELECT_LAST_BY_PATTERN_LOG_RECORD, ACCLOG_PATTERN, ACCLOG_COUNT);
	}
	
	public String[][] getLastRecordsByProject() throws SQLException, IOException {
		return getLastRecords(SQL_MAP_SELECT_LAST_BY_PROJECT_LOG_RECORD, ACCLOG_PROJECT, ACCLOG_COUNT);
	}
	
	public String[][] getLastRecordsByURI() throws SQLException, IOException {
		return getLastRecords(SQL_MAP_SELECT_LAST_BY_URI_LOG_RECORD, ACCLOG_REQUEST_URI, ACCLOG_COUNT);
	}
	
	public String[][] getRTRecordsByPattern() throws SQLException, IOException {
		return getLastRecords(SQL_MAP_SELECT_RT_BY_PATTERN_LOG_RECORD, ACCLOG_PATTERN, RESPONSE_TIME);
	}
	
	public String[][] getRTRecordsByProject() throws SQLException, IOException {
		return getLastRecords(SQL_MAP_SELECT_RT_BY_PROJECT_LOG_RECORD, ACCLOG_PROJECT, RESPONSE_TIME);
	}
	
	public String[][] getRTRecordsByURI() throws SQLException, IOException {
		return getLastRecords(SQL_MAP_SELECT_RT_BY_URI_LOG_RECORD, ACCLOG_REQUEST_URI, RESPONSE_TIME);
	}
	
	private String[][] getLastRecords(String sqlLocation, String fieldSeries, String fieldNumber) throws SQLException, IOException {
		try {
			checkDB();
			
			DataSource dataSource = RepositoryFacade.getInstance()
					.getDataSource();
			Connection connection = null;
			try {
				connection = dataSource.getConnection();
				DBUtils dbUtils = new DBUtils(dataSource);
				String sql = dbUtils.readScript(connection, sqlLocation, AccessLogRecordDAO.class);
				PreparedStatement pstmt = connection.prepareStatement(sql);
				
				GregorianCalendar last = new GregorianCalendar();
				last.add(Calendar.YEAR, -1);
				pstmt.setTimestamp(1, new Timestamp(last.getTime().getTime()));
				
				ResultSet rs = pstmt.executeQuery();
				List<List<Object>> allRecords = new ArrayList<List<Object>>();
				while (rs.next()) {
					String thisPattern = rs.getString(fieldSeries);
					Date thisPeriod = rs.getTimestamp(ACCLOG_PERIOD);
					int thisCount = rs.getInt(fieldNumber);
					
					List<Object> record = new ArrayList<Object>();
					record.add(thisPattern);
					record.add(thisPeriod);
					record.add(thisCount);
					
					allRecords.add(record);
				}
				
				String[][] result = prepareData(allRecords);
				
				return result;
				
			} finally {
				if (connection != null) {
					connection.close();
				}
			}
		} catch (NamingException e) {
			throw new SQLException(e);
		}
	}

	public String[][] prepareData(List<List<Object>> allRecords) {
		
		if (allRecords == null
				|| allRecords.size() == 0) {
			return null;
		}
		
		// add the monitored patterns and meanwhile take the min date
		Date minDate = new Date();
		List<String> patterns = new ArrayList<String>();
		patterns.add("date");
		for (List<Object> patternRecord : allRecords) {
			String pattern = (String) patternRecord.get(0);
			if (!patterns.contains(pattern)) {
				patterns.add(pattern);
			}
			Date date = (Date) patternRecord.get(1);
			if (minDate.after(date)) {
				minDate = date;
			}
		}
		
		// prepare date formats and beginning date 
		Calendar calendar = new GregorianCalendar();
		calendar.setTime(minDate);
		Date now = new Date();
		Map<String, String[]> prepared = new TreeMap<String, String[]>();
		SimpleDateFormat format = new SimpleDateFormat("yyyyMMddHH");
		
		// create header record
		String[] headerRecord = patterns.toArray(new String[]{});
		
		// prepare the empty matrix
		while (calendar.getTime().before(now)) {
			String[] record = new String[patterns.size()];
			String dateKey = format.format(calendar.getTime());
			record[0] = dateKey;
			for (int i=1;i<record.length;i++) {
				record[i] = "0";
			}
			prepared.put(dateKey, record);
			calendar.add(Calendar.HOUR_OF_DAY, 1);
		}
		
		// set the existing numbers to the corresponding places in the matrix
		for (List<Object> patternRecord : allRecords) {
			String pattern = (String) patternRecord.get(0);
			Date date = (Date) patternRecord.get(1);
			int count = (Integer) patternRecord.get(2);
			String dateKey = format.format(date);
			String[] record = prepared.get(dateKey);
			if (record != null) {
				record[patterns.indexOf(pattern)] = count + "";
			}
		}
		
		// prepare the final matrix
		String[][] result = new String[prepared.size() + 1][patterns.size()];
		int i = 1;
		result[0] = headerRecord;
		for (String[] record : prepared.values()) {
			for (int j = 0; j < record.length; j++) {
				result[i][j] = record[j];
			}
			i++;
		}
		
		return result;
	}
	

	public String[][] getHitsByURI() throws SQLException, IOException {
		try {
			checkDB();
			
			DataSource dataSource = RepositoryFacade.getInstance()
					.getDataSource();
			Connection connection = null;
			try {
				connection = dataSource.getConnection();
				DBUtils dbUtils = new DBUtils(dataSource);
				String sql = dbUtils.readScript(connection, SQL_MAP_SELECT_HITS_BY_URI_LOG_RECORD, AccessLogRecordDAO.class);
				PreparedStatement pstmt = connection.prepareStatement(sql);
				
				ResultSet rs = pstmt.executeQuery();
				List<String[]> allRecords = new ArrayList<String[]>();
				while (rs.next()) {
					String thisURI = rs.getString(ACCLOG_REQUEST_URI);
					int thisCount = rs.getInt(ACCLOG_COUNT);
					
					String[] record = new String[2];
					record[0] = thisURI;
					record[1] = thisCount  + "";
					
					allRecords.add(record);
				}
				
				String[][] result = new String[allRecords.size()][2];
				int i=0;
				for (Iterator<String[]> iterator = allRecords.iterator(); iterator
						.hasNext();) {
					String[] strings = iterator.next();
					for (int j=0;j<2;j++) {
						result[i][j] = strings[j];
					}
					i++;
				}
				
				return result;
				
			} finally {
				if (connection != null) {
					connection.close();
				}
			}
		} catch (NamingException e) {
			throw new SQLException(e);
		}
	}
}
