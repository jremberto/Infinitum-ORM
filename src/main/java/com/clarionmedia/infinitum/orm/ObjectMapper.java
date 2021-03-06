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

package com.clarionmedia.infinitum.orm;

import com.clarionmedia.infinitum.context.InfinitumContext;
import com.clarionmedia.infinitum.di.annotation.Autowired;
import com.clarionmedia.infinitum.internal.Pair;
import com.clarionmedia.infinitum.logging.Logger;
import com.clarionmedia.infinitum.logging.impl.SmartLogger;
import com.clarionmedia.infinitum.orm.exception.InvalidMappingException;
import com.clarionmedia.infinitum.orm.exception.ModelConfigurationException;
import com.clarionmedia.infinitum.orm.persistence.PersistencePolicy;
import com.clarionmedia.infinitum.orm.persistence.TypeAdapter;
import com.clarionmedia.infinitum.orm.persistence.TypeResolutionPolicy;
import com.clarionmedia.infinitum.orm.relationship.*;
import com.clarionmedia.infinitum.orm.rest.impl.RestfulNameValueMapper;
import com.clarionmedia.infinitum.orm.sqlite.impl.SqliteMapper;
import com.clarionmedia.infinitum.reflection.ClassReflector;

import java.lang.reflect.Field;
import java.util.Map;

/**
 * <p> {@code ObjectMapper} provides an API for mapping domain objects to database tables and vice versa. For mapping to
 * SQLite databases, see this class's concrete implementation {@link SqliteMapper} and for mapping to a RESTful web
 * service, see the {@link RestfulNameValueMapper} implementation. </p>
 *
 * @author Tyler Treat
 * @version 1.1.0 04/25/13
 * @since 1.0
 */
public abstract class ObjectMapper {

    @Autowired
    protected PersistencePolicy mPersistencePolicy;

    @Autowired
    protected TypeResolutionPolicy mTypePolicy;

    @Autowired
    protected ClassReflector mClassReflector;

    @Autowired
    protected InfinitumContext mContext;

    protected Logger mLogger;

    /**
     * Creates a new {@code ObjectMapper} instance.
     */
    public ObjectMapper() {
        mLogger = new SmartLogger(getClass().getSimpleName());
    }

    /**
     * Returns a {@link ModelMap} object containing persistent model data values mapped to their respective columns.
     *
     * @param model the {@link Object} to map
     * @return {@code ModelMap} with the entity's persistent fields mapped to their columns
     * @throws InvalidMappingException     if a type cannot be mapped
     * @throws ModelConfigurationException if the model is configured incorrectly
     */
    public abstract ModelMap mapModel(Object model) throws InvalidMappingException, ModelConfigurationException;

    /**
     * Registers the given {@link TypeAdapter} for the specified {@link Class} with this {@code SqliteMapper}
     * instance. The
     * {@code TypeAdapter} allows a {@link Field} of this type to be mapped to a database column. Registering a {@code
     * TypeAdapter} for a {@code Class} which already has a {@code TypeAdapter} registered for it will result in the
     * previous {@code TypeAdapter} being overridden.
     *
     * @param type    the {@code Class} this {@code TypeAdapter} is for
     * @param adapter the {@code TypeAdapter} to register
     */
    public abstract <T> void registerTypeAdapter(Class<T> type, TypeAdapter<T> adapter);

    /**
     * Returns a {@link Map} containing all {@link TypeAdapter} instances registered with this {@code ObjectMapper}
     * and the
     * {@link Class} instances in which they are registered for.
     *
     * @return {@code Map<Class<?>, TypeAdapter<?>>
     */
    public abstract Map<Class<?>, ? extends TypeAdapter<?>> getRegisteredTypeAdapters();

    /**
     * Retrieves the {@link TypeAdapter} registered for the given {@link Class}.
     *
     * @param type the {@code Class} to retrieve the {@code TypeAdapter} for
     * @return {@code TypeAdapter} for the specified type
     * @throws InvalidMappingException if there is no registered {@code TypeAdapter} for the given {@code Class}
     */
    public abstract <T> TypeAdapter<T> resolveType(Class<T> type) throws InvalidMappingException;

    /**
     * Indicates if the given {@link Field} is a "text" data type as represented in a database.
     *
     * @param f the {@code Field} to check
     * @return {@code true} if it is a text type, {@code false} if not
     */
    public abstract boolean isTextColumn(Field f);

    /**
     * Maps the given relationship {@link Field} to the given {@link ModelMap}.
     *
     * @param map   the {@code ModelMap} to add the relationship to
     * @param model the model containing the relationship
     * @param field the relationship {@code Field}
     */
    @SuppressWarnings("unchecked")
    protected void mapRelationship(ModelMap map, Object model, Field field) {
        if (mPersistencePolicy.isRelationship(field)) {
            ModelRelationship rel = mPersistencePolicy.getRelationship(field);
            Object related;
            switch (rel.getRelationType()) {
                case ManyToMany:
                    ManyToManyRelationship mtm = (ManyToManyRelationship) rel;
                    related = mClassReflector.getFieldValue(model, field);
                    if (!(related instanceof Iterable))
                        throw new ModelConfigurationException(String.format(
                                "Field '%s' is marked as a many-to-many relationship in '%s', " +
                                        "but it is not a collection.", field.getName(),
                                field.getDeclaringClass().getName()));
                    map.addManyToManyRelationship(new Pair<ManyToManyRelationship, Iterable<Object>>(mtm,
                            (Iterable<Object>) related));
                    break;
                case ManyToOne:
                    ManyToOneRelationship mto = (ManyToOneRelationship) rel;
                    related = mClassReflector.getFieldValue(model, field);
                    if (related != null && !mTypePolicy.isDomainModel(related.getClass()))
                        throw new ModelConfigurationException(String.format(
                                "Field '%s' is marked as a many-to-one relationship in '%s', " +
                                        "but it is not a domain entity.", field.getName(),
                                field.getDeclaringClass().getName()));
                    map.addManyToOneRelationship(new Pair<ManyToOneRelationship, Object>(mto, related));
                    break;
                case OneToMany:
                    OneToManyRelationship otm = (OneToManyRelationship) rel;
                    related = mClassReflector.getFieldValue(model, field);
                    if (!(related instanceof Iterable))
                        throw new ModelConfigurationException(String.format(
                                "Field '%s' is marked as a one-to-many relationship in '%s', " +
                                        "but it is not a collection.", field.getName(),
                                field.getDeclaringClass().getName()));
                    map.addOneToManyRelationship(new Pair<OneToManyRelationship, Iterable<Object>>(otm, (Iterable<Object>) related));
                    break;
                case OneToOne:
                    OneToOneRelationship oto = (OneToOneRelationship) rel;
                    related = mClassReflector.getFieldValue(model, field);
                    if (related != null && !mTypePolicy.isDomainModel(related.getClass()))
                        throw new ModelConfigurationException(String.format(
                                "Field '%s' is marked as a one-to-one relationship in '%s', but it is not a domain entity.", field.getName(),
                                field.getDeclaringClass().getName()));
                    map.addOneToOneRelationship(new Pair<OneToOneRelationship, Object>(oto, related));
                    break;
            }
        }
    }

}
