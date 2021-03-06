/*
 * Copyright (C) 2013 Clarion Media, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.clarionmedia.infinitum.orm.sqlite.impl;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.clarionmedia.infinitum.logging.Logger;
import com.clarionmedia.infinitum.logging.impl.SmartLogger;
import com.clarionmedia.infinitum.orm.context.InfinitumOrmContext;
import com.clarionmedia.infinitum.orm.exception.ModelConfigurationException;
import com.clarionmedia.infinitum.orm.sql.SqlBuilder;

/**
 * <p> A helper class to manage database creation and version management. This is an extension of {@link
 * SQLiteOpenHelper} that will take care of opening a database, creating it if it does not exist, and upgrading it if
 * necessary. </p>
 *
 * @author Tyler Treat
 * @version 1.1.0 04/25/13
 * @since 1.0
 */
public class SqliteDbHelper extends SQLiteOpenHelper {

    private static SqliteDbHelper sInstance;

    private SqlBuilder mSqlBuilder;
    private SQLiteDatabase mSqliteDb;
    private InfinitumOrmContext mInfinitumContext;
    private Logger mLogger;

    /**
     * Constructs a new {@code SqliteDbHelper} with the given {@link Context} and {@link SqliteMapper}.
     *
     * @param context    the {@link InfinitumOrmContext} of the {@code SqliteDbHelper}
     * @param sqlBuilder the {@code SqlBuilder} to use
     */
    private SqliteDbHelper(InfinitumOrmContext context, SqlBuilder sqlBuilder) {
        super(context.getAndroidContext(), context.getSqliteDbName(), null, context.getSqliteDbVersion());
        mLogger = new SmartLogger(getClass().getSimpleName());
        mInfinitumContext = context;
        mSqlBuilder = sqlBuilder;
    }

    /**
     * Returns a singleton instance of {@code SqliteDbHelper}.
     *
     * @param context    the {@link InfinitumOrmContext} of the {@code SqliteDbHelper}
     * @param sqlBuilder the {@code SqlBuilder} to use
     * @return {@code SqliteDbHelper} singleton
     */
    public static SqliteDbHelper getInstance(InfinitumOrmContext context, SqlBuilder sqlBuilder) {
        if (sInstance == null) {
            sInstance = new SqliteDbHelper(context, sqlBuilder);
        }
        return sInstance;
    }

    /**
     * Returns an instance of the {@link SQLiteDatabase}.
     *
     * @return the {@code SQLiteDatabase} for this application
     */
    public SQLiteDatabase getDatabase() {
        return mSqliteDb;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        mSqliteDb = db;
        if (!mInfinitumContext.isSchemaGenerated())
            return;
        mLogger.debug("Creating database tables");
        try {
            mSqlBuilder.createTables(this);
        } catch (ModelConfigurationException e) {
            mLogger.error("Error creating database tables.", e);
        }
        mLogger.debug("Database tables created successfully");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        mLogger.debug("Upgrading database from version " + oldVersion + " to " + newVersion
                + ", which will destroy all old data");
        mSqliteDb = db;
        mSqlBuilder.dropTables(this);
        mLogger.debug("Database tables dropped successfully");
        onCreate(db);
    }

}
