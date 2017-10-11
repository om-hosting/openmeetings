/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License") +  you may not use this file except in compliance
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
package org.apache.openmeetings.service.calendar.caldav.handler;

import static javax.servlet.http.HttpServletResponse.SC_OK;
import static org.apache.openmeetings.util.OpenmeetingsVariables.getWebAppRootKey;

import java.io.IOException;

import org.apache.commons.httpclient.HttpClient;
import org.apache.jackrabbit.webdav.DavException;
import org.apache.jackrabbit.webdav.MultiStatusResponse;
import org.apache.jackrabbit.webdav.client.methods.DavMethodBase;
import org.apache.jackrabbit.webdav.property.DavPropertyName;
import org.apache.jackrabbit.webdav.property.DavPropertyNameSet;
import org.apache.jackrabbit.webdav.property.DavPropertySet;
import org.apache.jackrabbit.webdav.xml.Namespace;
import org.apache.openmeetings.db.dao.calendar.AppointmentDao;
import org.apache.openmeetings.db.entity.calendar.Appointment;
import org.apache.openmeetings.db.entity.calendar.OmCalendar;
import org.apache.openmeetings.service.calendar.caldav.AppointmentManager;
import org.apache.openmeetings.service.calendar.caldav.IcalUtils;
import org.osaf.caldav4j.CalDAVConstants;
import org.osaf.caldav4j.methods.PropFindMethod;
import org.red5.logging.Red5LoggerFactory;
import org.slf4j.Logger;

/**
 * Class for Syncing through the help of Ctags.
 * It checks if the Ctag of the Calendar has changed.
 * If it has then update the events, otherwise leave it as it is.
 *
 * @see CalendarHandler
 */
public class CtagHandler extends AbstractCalendarHandler {
	private static final Logger log = Red5LoggerFactory.getLogger(CtagHandler.class, getWebAppRootKey());

	public static final Namespace NAMESPACE_CALSERVER = Namespace.getNamespace("cs", "http://calendarserver.org/ns/");
	public static final DavPropertyName DNAME_GETCTAG = DavPropertyName.create("getctag", NAMESPACE_CALSERVER);

	public CtagHandler(String path, OmCalendar calendar, HttpClient client, AppointmentDao appointmentDao, IcalUtils utils) {
		super(path, calendar, client, appointmentDao, utils);
	}

	@Override
	DavMethodBase internalSyncItems() throws IOException, DavException {
		//Calendar already inited.

		DavPropertyNameSet properties = new DavPropertyNameSet();
		properties.add(DNAME_GETCTAG);

		PropFindMethod method = new PropFindMethod(path, properties, CalDAVConstants.DEPTH_0);
		client.executeMethod(method);

		if (method.succeeded()) {
			for (MultiStatusResponse response : method.getResponseBodyAsMultiStatus().getResponses()) {
				DavPropertySet set = response.getProperties(SC_OK);
				String ctag = AppointmentManager.getTokenFromProperty(set.get(DNAME_GETCTAG));

				if (ctag != null && !ctag.equals(calendar.getToken())) {
					EtagsHandler etagsHandler = new EtagsHandler(path, calendar, client, appointmentDao, utils);
					etagsHandler.syncItems();
					calendar.setToken(ctag);
				}
			}
		} else {
			log.error("Error executing PROPFIND Method, with status Code: {}", method.getStatusCode());
		}
		return method;
	}

	@Override
	public boolean updateItem(Appointment appointment) {
		EtagsHandler etagsHandler = new EtagsHandler(path, calendar, client, appointmentDao, utils);
		return etagsHandler.updateItem(appointment);
	}

	@Override
	public boolean deleteItem(Appointment appointment) {
		EtagsHandler etagsHandler = new EtagsHandler(path, calendar, client, appointmentDao, utils);
		return etagsHandler.deleteItem(appointment);
	}
}
