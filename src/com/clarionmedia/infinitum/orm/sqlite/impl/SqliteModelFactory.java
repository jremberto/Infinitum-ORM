/*
 * Copyright (C) 2012 Clarion Media, LLC
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

import java.io.Serializable;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.List;

import android.database.Cursor;

import com.clarionmedia.infinitum.di.annotation.Autowired;
import com.clarionmedia.infinitum.exception.InfinitumRuntimeException;
import com.clarionmedia.infinitum.orm.LazyLoadDexMakerProxy;
import com.clarionmedia.infinitum.orm.ModelFactory;
import com.clarionmedia.infinitum.orm.ResultSet;
import com.clarionmedia.infinitum.orm.exception.ModelConfigurationException;
import com.clarionmedia.infinitum.orm.persistence.PersistencePolicy;
import com.clarionmedia.infinitum.orm.relationship.ForeignKeyRelationship;
import com.clarionmedia.infinitum.orm.relationship.ManyToManyRelationship;
import com.clarionmedia.infinitum.orm.relationship.ManyToOneRelationship;
import com.clarionmedia.infinitum.orm.relationship.ModelRelationship;
import com.clarionmedia.infinitum.orm.relationship.OneToManyRelationship;
import com.clarionmedia.infinitum.orm.relationship.OneToOneRelationship;
import com.clarionmedia.infinitum.orm.sqlite.SqliteTypeAdapter;
import com.clarionmedia.infinitum.reflection.ClassReflector;

/**
 * <p>
 * This is an implementation of {@link ModelFactory} for processing
 * {@link SqliteResult} queries.
 * </p>
 * 
 * @author Tyler Treat
 * @version 1.0 02/20/12
 * @since 1.0
 */
public class SqliteModelFactory implements ModelFactory {

	@Autowired
	private SqliteBuilder mSqlBuilder;

	@Autowired
	private SqliteSession mSession;

	@Autowired
	private SqliteMapper mMapper;

	@Autowired
	private PersistencePolicy mPersistencePolicy;

	@Autowired
	private ClassReflector mClassReflector;

	@Override
	public <T> T createFromResult(ResultSet result, Class<T> modelClass) {
		if (!(result instanceof SqliteResult))
			throw new IllegalArgumentException("SqliteModelFactory can only process SqliteResults.");
		return createFromCursorRec(((SqliteResult) result).getCursor(), modelClass);
	}

	/**
	 * Constructs a domain model instance and populates its {@link Field}'s from
	 * the given {@link Cursor}. The precondition for this method is that the
	 * {@code Cursor} is currently at the row to convert to an {@link Object}
	 * from the correct table.
	 * 
	 * @param cursor
	 *            the {@code Cursor} containing the row to convert to an
	 *            {@code Object}
	 * @param modelClass
	 *            the {@code Class} of the {@code Object} being instantiated
	 * @return a populated instance of the specified {@code Class}
	 * @throws ModelConfigurationException
	 *             if the specified model {@code Class} does not contain an
	 *             empty constructor
	 * @throws InfinitumRuntimeException
	 *             if the model could not be instantiated
	 */
	public <T> T createFromCursor(Cursor cursor, Class<T> modelClass) throws ModelConfigurationException, InfinitumRuntimeException {
		return createFromCursorRec(cursor, modelClass);
	}

	@SuppressWarnings("unchecked")
	private <T> T createFromCursorRec(Cursor cursor, Class<T> modelClass) throws ModelConfigurationException, InfinitumRuntimeException {
		T ret = null;
		SqliteResult result = new SqliteResult(cursor);
		ret = (T) mClassReflector.getClassInstance(modelClass);
		List<Field> fields = mPersistencePolicy.getPersistentFields(modelClass);
		for (Field field : fields) {
			field.setAccessible(true);
			if (!mPersistencePolicy.isRelationship(field)) {
				SqliteTypeAdapter<?> resolver = mMapper.resolveType(field.getType());
				int index = result.getColumnIndex(mPersistencePolicy.getFieldColumnName(field));
				try {
					resolver.mapToObject(result, index, field, ret);
				} catch (IllegalArgumentException e) {
					throw new InfinitumRuntimeException("Could not map '" + field.getType().getName() + "'");
				} catch (IllegalAccessException e) {
					throw new InfinitumRuntimeException("Could not map '" + field.getType().getName() + "'");
				}
			}
		}
		int objHash = mPersistencePolicy.computeModelHash(ret);
		if (mSession.checkCache(objHash))
			return (T) mSession.searchCache(objHash);
		mSession.cache(objHash, ret);
		loadRelationships(ret, cursor);
		return ret;
	}

	private <T> void loadRelationships(T model, Cursor cursor) throws ModelConfigurationException, InfinitumRuntimeException {
		for (Field f : mPersistencePolicy.getPersistentFields(model.getClass())) {
			f.setAccessible(true);
			if (!mPersistencePolicy.isRelationship(f))
				continue;
			ModelRelationship rel = mPersistencePolicy.getRelationship(f);
			Serializable fk = null;
			switch (rel.getRelationType()) {
			case ManyToMany:
				if (mPersistencePolicy.isLazy(model.getClass()))
					lazilyLoadManyToMany((ManyToManyRelationship) rel, f, model);
				else
					loadManyToMany((ManyToManyRelationship) rel, f, model);
				break;
			case ManyToOne:
				ManyToOneRelationship mto = (ManyToOneRelationship) rel;
				fk = cursor.getString(cursor.getColumnIndex(mto.getColumn()));
				if (mPersistencePolicy.isLazy(model.getClass()))
					lazilyLoadManyToOne(mto, f, model, fk);
				else
					loadManyToOne(mto, f, model, fk);
				break;
			case OneToMany:
				if (mPersistencePolicy.isLazy(model.getClass()))
					lazilyLoadOneToMany((OneToManyRelationship) rel, f, model);
				else
					loadOneToMany((OneToManyRelationship) rel, f, model);
				break;
			case OneToOne:
				OneToOneRelationship oto = (OneToOneRelationship) rel;
				int col = cursor.getColumnIndex(oto.getColumn());
				if (col > -1)
					fk = cursor.getString(col);
				if (mPersistencePolicy.isLazy(model.getClass()))
					lazilyLoadOneToOne(oto, f, model, fk);
				else
					loadOneToOne(oto, f, model, fk);
				break;
			}
		}
	}

	private <T> void lazilyLoadOneToOne(final OneToOneRelationship rel, Field field, T model, Serializable foreignKey) {
		final String sql = getOneToOneEntityQuery(model, rel.getSecondType(), field, rel, foreignKey);
		Object related = null;
		if (mSession.count(sql.replace("*", "count(*)")) > 0) {
			related = new LazyLoadDexMakerProxy(mSession.getContext(), rel.getSecondType()) {
				@Override
				protected Object loadObject() {
					boolean close = false;
					if (!mSession.isOpen()) {
						mSession.open();
						close = true;
					}
					Object ret = null;
					Cursor result = mSession.executeForResult(sql);
					try {
						while (result.moveToNext())
							ret = createFromCursor(result, rel.getSecondType());
					} finally {
						result.close();
						if (close)
							mSession.close();
					}
					return ret;
				}
			}.getProxy();
		}
		mClassReflector.setFieldValue(model, field, related);
	}

	private <T> void loadOneToOne(OneToOneRelationship rel, Field field, T model, Serializable foreignKey) {
		String sql = getOneToOneEntityQuery(model, rel.getSecondType(), field, rel, foreignKey);
		Cursor result = mSession.executeForResult(sql);
		try {
			while (result.moveToNext())
				mClassReflector.setFieldValue(model, field, createFromCursor(result, rel.getSecondType()));
		} finally {
			result.close();
		}
	}

	private <T> void lazilyLoadOneToMany(final OneToManyRelationship rel, Field field, T model) {
		final StringBuilder sql = new StringBuilder("SELECT * FROM ").append(mPersistencePolicy.getModelTableName(rel.getManyType()))
				.append(" WHERE ").append(rel.getColumn()).append(" = ");
		Serializable pk = mPersistencePolicy.getPrimaryKey(model);
		switch (mMapper.getSqliteDataType(mPersistencePolicy.getPrimaryKeyField(model.getClass()))) {
		case TEXT:
			sql.append("'").append(pk).append("'");
			break;
		default:
			sql.append(pk);
		}
		@SuppressWarnings("unchecked")
		final Collection<Object> collection = (Collection<Object>) mClassReflector.getFieldValue(model, field);
		@SuppressWarnings("unchecked")
		Collection<Object> related = (Collection<Object>) new LazyLoadDexMakerProxy(mSession.getContext(), collection.getClass()) {
			@Override
			protected Object loadObject() {
				boolean close = false;
				if (!mSession.isOpen()) {
					mSession.open();
					close = true;
				}
				Cursor result = mSession.executeForResult(sql.toString());
				try {
					while (result.moveToNext())
						collection.add(createFromCursor(result, rel.getManyType()));
				} finally {
					result.close();
					if (close)
						mSession.close();
				}
				return collection;
			}
		}.getProxy();
		mClassReflector.setFieldValue(model, field, related);
	}

	private <T> void loadOneToMany(OneToManyRelationship rel, Field field, T model) {
		StringBuilder sql = new StringBuilder("SELECT * FROM ").append(mPersistencePolicy.getModelTableName(rel.getManyType()))
				.append(" WHERE ").append(rel.getColumn()).append(" = ");
		Serializable pk = mPersistencePolicy.getPrimaryKey(model);
		switch (mMapper.getSqliteDataType(mPersistencePolicy.getPrimaryKeyField(model.getClass()))) {
		case TEXT:
			sql.append("'").append(pk).append("'");
			break;
		default:
			sql.append(pk);
		}
		@SuppressWarnings("unchecked")
		Collection<Object> related = (Collection<Object>) mClassReflector.getFieldValue(model, field);
		Cursor result = mSession.executeForResult(sql.toString());
		try {
			while (result.moveToNext())
				related.add(createFromCursor(result, rel.getManyType()));
		} finally {
			result.close();
		}
		mClassReflector.setFieldValue(model, field, related);
	}

	private <T> void lazilyLoadManyToOne(ManyToOneRelationship rel, Field field, T model, Serializable foreignKey) {
		final Class<?> direction = model.getClass() == rel.getFirstType() ? rel.getSecondType() : rel.getFirstType();
		final String sql = getEntityQuery(model, direction, field, rel, foreignKey);
		Object related = null;
		if (mSession.count(sql.replace("*", "count(*)")) > 0) {
			related = new LazyLoadDexMakerProxy(mSession.getContext(), rel.getSecondType()) {
				@Override
				protected Object loadObject() {
					boolean close = false;
					if (!mSession.isOpen()) {
						mSession.open();
						close = true;
					}
					Object ret = null;
					Cursor result = mSession.executeForResult(sql);
					try {
						while (result.moveToNext())
							ret = createFromCursor(result, direction);
					} finally {
						result.close();
						if (close)
							mSession.close();
					}
					return ret;
				}
			}.getProxy();
		}
		mClassReflector.setFieldValue(model, field, related);
	}

	private <T> void loadManyToOne(ManyToOneRelationship rel, Field field, T model, Serializable foreignKey) {
		Class<?> direction = model.getClass() == rel.getFirstType() ? rel.getSecondType() : rel.getFirstType();
		String sql = getEntityQuery(model, direction, field, rel, foreignKey);
		Cursor result = mSession.executeForResult(sql);
		try {
			while (result.moveToNext())
				mClassReflector.setFieldValue(model, field, createFromCursor(result, direction));
		} finally {
			result.close();
		}
	}

	private <T> void lazilyLoadManyToMany(final ManyToManyRelationship rel, Field field, T model) {
		// TODO Add reflexive M:M support
		final Class<?> direction = model.getClass() == rel.getFirstType() ? rel.getSecondType() : rel.getFirstType();
		Serializable pk = mPersistencePolicy.getPrimaryKey(model);
		final String sql = mSqlBuilder.createManyToManyJoinQuery(rel, pk, direction);
		@SuppressWarnings("unchecked")
		final Collection<Object> collection = (Collection<Object>) mClassReflector.getFieldValue(model, field);
		@SuppressWarnings("unchecked")
		Collection<Object> related = (Collection<Object>) new LazyLoadDexMakerProxy(mSession.getContext(), collection.getClass()) {
			@Override
			protected Object loadObject() {
				boolean close = false;
				if (!mSession.isOpen()) {
					mSession.open();
					close = true;
				}
				Cursor result = mSession.executeForResult(sql);
				try {
					while (result.moveToNext())
						collection.add(createFromCursor(result, direction));
				} finally {
					result.close();
					if (close)
						mSession.close();
				}
				return collection;
			}
		}.getProxy();
		mClassReflector.setFieldValue(model, field, related);
	}

	private <T> void loadManyToMany(ManyToManyRelationship rel, Field f, T model) throws ModelConfigurationException,
			InfinitumRuntimeException {
		// TODO Add reflexive M:M support
		Class<?> direction = model.getClass() == rel.getFirstType() ? rel.getSecondType() : rel.getFirstType();
		Serializable pk = mPersistencePolicy.getPrimaryKey(model);
		String sql = mSqlBuilder.createManyToManyJoinQuery(rel, pk, direction);
		Cursor result = mSession.executeForResult(sql);
		@SuppressWarnings("unchecked")
		Collection<Object> related = (Collection<Object>) mClassReflector.getFieldValue(model, f);
		try {
			while (result.moveToNext())
				related.add(createFromCursor(result, direction));
		} finally {
			result.close();
		}
		mClassReflector.setFieldValue(model, f, related);
	}

	private String getEntityQuery(Object model, Class<?> c, Field field, ForeignKeyRelationship rel, Serializable foreignKey) {
		StringBuilder sql = new StringBuilder("SELECT * FROM ").append(mPersistencePolicy.getModelTableName(c)).append(" WHERE ")
				.append(mPersistencePolicy.getFieldColumnName(mPersistencePolicy.getPrimaryKeyField(c))).append(" = ");
		switch (mMapper.getSqliteDataType(field)) {
		case TEXT:
			sql.append("'").append(foreignKey).append("'");
			break;
		default:
			sql.append(foreignKey);
		}
		return sql.append(" LIMIT 1").toString();
	}

	private String getOneToOneEntityQuery(Object model, Class<?> relatedClass, Field field, OneToOneRelationship rel,
			Serializable foreignKey) {
		boolean isOwner = rel.getOwner() == model.getClass();
		StringBuilder sql = new StringBuilder("SELECT * FROM ").append(mPersistencePolicy.getModelTableName(relatedClass))
				.append(" WHERE ");
		if (isOwner) {
			sql.append(mPersistencePolicy.getFieldColumnName(mPersistencePolicy.getPrimaryKeyField(relatedClass)));
		} else {
			sql.append(rel.getColumn());
		}
		sql.append(" = ");
		Serializable relKey = isOwner ? foreignKey : mPersistencePolicy.getPrimaryKey(model);
		switch (mMapper.getSqliteDataType(field)) {
		case TEXT:
			sql.append("'").append(relKey).append("'");
			break;
		default:
			sql.append(relKey);
		}
		return sql.append(" LIMIT 1").toString();
	}

}
