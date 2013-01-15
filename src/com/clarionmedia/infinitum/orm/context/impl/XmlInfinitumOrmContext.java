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

package com.clarionmedia.infinitum.orm.context.impl;

import static java.lang.Boolean.parseBoolean;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.content.Context;

import com.clarionmedia.infinitum.activity.LifecycleEvent;
import com.clarionmedia.infinitum.context.InfinitumContext;
import com.clarionmedia.infinitum.context.RestfulContext;
import com.clarionmedia.infinitum.context.RestfulContext.MessageType;
import com.clarionmedia.infinitum.context.exception.InfinitumConfigurationException;
import com.clarionmedia.infinitum.context.impl.XmlApplicationContext;
import com.clarionmedia.infinitum.di.AbstractBeanDefinition;
import com.clarionmedia.infinitum.di.BeanDefinitionBuilder;
import com.clarionmedia.infinitum.di.BeanFactory;
import com.clarionmedia.infinitum.event.EventSubscriber;
import com.clarionmedia.infinitum.internal.ModuleUtils;
import com.clarionmedia.infinitum.internal.ModuleUtils.Module;
import com.clarionmedia.infinitum.orm.Session;
import com.clarionmedia.infinitum.orm.context.InfinitumOrmContext;
import com.clarionmedia.infinitum.orm.persistence.PersistencePolicy;
import com.clarionmedia.infinitum.orm.persistence.impl.AnnotationsPersistencePolicy;
import com.clarionmedia.infinitum.orm.persistence.impl.DefaultTypeResolutionPolicy;
import com.clarionmedia.infinitum.orm.persistence.impl.XmlPersistencePolicy;
import com.clarionmedia.infinitum.orm.rest.impl.RestfulJsonMapper;
import com.clarionmedia.infinitum.orm.rest.impl.RestfulJsonSession;
import com.clarionmedia.infinitum.orm.rest.impl.RestfulNameValueMapper;
import com.clarionmedia.infinitum.orm.rest.impl.RestfulSession;
import com.clarionmedia.infinitum.orm.rest.impl.RestfulXmlMapper;
import com.clarionmedia.infinitum.orm.rest.impl.RestfulXmlSession;
import com.clarionmedia.infinitum.orm.sqlite.SqliteUtils;
import com.clarionmedia.infinitum.orm.sqlite.impl.SqliteBuilder;
import com.clarionmedia.infinitum.orm.sqlite.impl.SqliteMapper;
import com.clarionmedia.infinitum.orm.sqlite.impl.SqliteModelFactory;
import com.clarionmedia.infinitum.orm.sqlite.impl.SqliteSession;
import com.clarionmedia.infinitum.orm.sqlite.impl.SqliteTemplate;
import com.clarionmedia.infinitum.reflection.ClassReflector;
import com.clarionmedia.infinitum.reflection.impl.JavaClassReflector;

/**
 * <p>
 * Implementation of {@link InfinitumOrmContext} which is initialized through
 * XML as a child of an {@link XmlApplicationContext} instance.
 * </p>
 * 
 * @author Tyler Treat
 * @version 1.0 12/23/12
 * @since 1.0
 */
public class XmlInfinitumOrmContext implements InfinitumOrmContext {

	private XmlApplicationContext mParentContext;
	private List<InfinitumContext> mChildContexts;
	private ClassReflector mClassReflector;

	/**
	 * Creates a new {@code XmlInfinitumOrmContext} instance as a child of the
	 * given {@link XmlApplicationContext}.
	 * 
	 * @param parentContext
	 *            the parent of this context
	 */
	public XmlInfinitumOrmContext(XmlApplicationContext parentContext) {
		mParentContext = parentContext;
		mChildContexts = new ArrayList<InfinitumContext>();
		mClassReflector = new JavaClassReflector();
	}

	@Override
	public void postProcess(Context context) {
	}

	@Override
	public List<AbstractBeanDefinition> getBeans(BeanDefinitionBuilder beanDefinitionBuilder) {
		List<AbstractBeanDefinition> beans = new ArrayList<AbstractBeanDefinition>();
		beans.add(beanDefinitionBuilder.setName("_" + InfinitumOrmContext.class.getSimpleName()).setType(XmlInfinitumOrmContext.class)
				.build());
		Map<String, Object> properties = new HashMap<String, Object>();
		properties.put("mIsAutocommit", isAutocommit());
		beans.add(beanDefinitionBuilder.setName("_" + SqliteTemplate.class.getSimpleName()).setType(SqliteTemplate.class)
				.setProperties(properties).build());
		beans.add(beanDefinitionBuilder.setName("_" + SqliteSession.class.getSimpleName()).setType(SqliteSession.class).build());
		beans.add(beanDefinitionBuilder.setName("_" + SqliteMapper.class.getSimpleName()).setType(SqliteMapper.class).build());
		beans.add(beanDefinitionBuilder.setName("_" + DefaultTypeResolutionPolicy.class.getSimpleName())
				.setType(DefaultTypeResolutionPolicy.class).build());
		beans.add(beanDefinitionBuilder.setName("_" + SqliteModelFactory.class.getSimpleName()).setType(SqliteModelFactory.class).build());
		beans.add(beanDefinitionBuilder.setName("_" + SqliteBuilder.class.getSimpleName()).setType(SqliteBuilder.class).build());
		beans.add(beanDefinitionBuilder.setName("_" + SqliteUtils.class.getSimpleName()).setType(SqliteUtils.class).build());
		beans.add(beanDefinitionBuilder.setName("_" + RestfulXmlMapper.class.getSimpleName()).setType(RestfulXmlMapper.class).build());
		beans.add(beanDefinitionBuilder.setName("_" + RestfulJsonMapper.class.getSimpleName()).setType(RestfulJsonMapper.class).build());
		beans.add(beanDefinitionBuilder.setName("_" + RestfulNameValueMapper.class.getSimpleName()).setType(RestfulNameValueMapper.class)
				.build());
		beans.add(beanDefinitionBuilder.setName("_" + RestfulJsonSession.class.getSimpleName()).setType(RestfulJsonSession.class).build());
		beans.add(beanDefinitionBuilder.setName("_" + RestfulXmlSession.class.getSimpleName()).setType(RestfulXmlSession.class).build());
		Class<?> type = getConfigurationMode() == ConfigurationMode.ANNOTATION ? AnnotationsPersistencePolicy.class
				: XmlPersistencePolicy.class;
		beans.add(beanDefinitionBuilder.setName("_" + PersistencePolicy.class.getSimpleName()).setType(type).build());
		return beans;
	}

	@Override
	public Session getSession(SessionType source) throws InfinitumConfigurationException {
		Session session;
		switch (source) {
		case SQLITE:
			session = getBean("_" + SqliteSession.class.getSimpleName(), SqliteSession.class);
			break;
		case REST:
			String client = mParentContext.getRestContext().getClientBean();
			if (client == null) {
				MessageType messageType = mParentContext.getRestContext().getMessageType();
				// Resolve Session implementation if no client is defined
				switch (messageType) {
				case JSON:
					session = getBean("_" + RestfulJsonSession.class.getSimpleName(), RestfulJsonSession.class);
					break;
				case XML:
					session = getBean("_" + RestfulXmlSession.class.getSimpleName(), RestfulXmlSession.class);
					break;
				default:
					throw new InfinitumConfigurationException("No qualifying Session implementation found.");
				}
			} else {
				// Otherwise use the preferred client
				session = getBean(client, RestfulSession.class);
			}
			break;
		default:
			throw new InfinitumConfigurationException("Session type not configured.");
		}
		if (ModuleUtils.hasModule(Module.UI)) {
			@SuppressWarnings("unchecked")
			InfinitumContext uiContext = getChildContext((Class<InfinitumContext>) mClassReflector.getClass(Module.UI.getContextClass()));
			Method getProxiedSession = mClassReflector.getMethod(uiContext.getClass(), "getProxiedSession", Session.class);
			return (Session) mClassReflector.invokeMethod(uiContext, getProxiedSession, session);
		}
		return session;
	}

	@Override
	public PersistencePolicy getPersistencePolicy() {
		return getBean("_" + PersistencePolicy.class.getSimpleName(), PersistencePolicy.class);
	}

	@Override
	public ConfigurationMode getConfigurationMode() {
		String mode = mParentContext.getAppConfig().get("mode");
		if (mode == null)
			return ConfigurationMode.ANNOTATION;
		if (mode.equalsIgnoreCase(ConfigurationMode.XML.toString()))
			return ConfigurationMode.XML;
		else if (mode.equalsIgnoreCase(ConfigurationMode.ANNOTATION.toString()))
			return ConfigurationMode.ANNOTATION;
		throw new InfinitumConfigurationException("Unknown configuration mode '" + mode + "'.");
	}

	@Override
	public boolean hasSqliteDb() {
		return mParentContext.getSqliteConfig() != null;
	}

	@Override
	public String getSqliteDbName() {
		String dbName = mParentContext.getSqliteConfig().get("dbName");
		if (dbName == null || dbName.length() == 0)
			throw new InfinitumConfigurationException("SQLite database name not specified.");
		return dbName;
	}

	@Override
	public int getSqliteDbVersion() {
		String dbVersion = mParentContext.getSqliteConfig().get("dbVersion");
		if (dbVersion == null)
			throw new InfinitumConfigurationException("SQLite database version not specified.");
		return Integer.parseInt(dbVersion);
	}

	@Override
	public List<String> getDomainTypes() {
		List<String> models = new ArrayList<String>();
		for (XmlApplicationContext.Model model : mParentContext.getModels()) {
			models.add(model.getResource());
		}
		return models;
	}

	@Override
	public boolean isSchemaGenerated() {
		String isGenerated = mParentContext.getSqliteConfig().get("generateSchema");
		if (isGenerated == null)
			return true;
		return parseBoolean(isGenerated);
	}

	@Override
	public boolean isAutocommit() {
		String autocommit = mParentContext.getSqliteConfig().get("autocommit");
		if (autocommit == null)
			return true;
		return parseBoolean(autocommit);
	}

	@Override
	public boolean isDebug() {
		return mParentContext.isDebug();
	}

	@Override
	public Context getAndroidContext() {
		return mParentContext.getAndroidContext();
	}

	@Override
	public BeanFactory getBeanFactory() {
		return mParentContext.getBeanFactory();
	}

	@Override
	public Object getBean(String name) {
		return mParentContext.getBean(name);
	}

	@Override
	public <T> T getBean(String name, Class<T> clazz) {
		return mParentContext.getBean(name, clazz);
	}

	@Override
	public boolean isComponentScanEnabled() {
		return mParentContext.isComponentScanEnabled();
	}

	@Override
	public List<InfinitumContext> getChildContexts() {
		return mChildContexts;
	}

	@Override
	public void addChildContext(InfinitumContext context) {
		mChildContexts.add(context);
	}

	@Override
	public InfinitumContext getParentContext() {
		return mParentContext;
	}

	@Override
	public <T extends InfinitumContext> T getChildContext(Class<T> contextType) {
		return mParentContext.getChildContext(contextType);
	}

	@Override
	public RestfulContext getRestContext() {
		return mParentContext.getRestContext();
	}

	@Override
	public void publishEvent(LifecycleEvent event) {
		mParentContext.publishEvent(event);
	}

	@Override
	public void subscribeForEvents(EventSubscriber subscriber) {
		mParentContext.subscribeForEvents(subscriber);
	}

}
