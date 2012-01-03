/**
 * Licensed to Jasig under one or more contributor license
 * agreements. See the NOTICE file distributed with this work
 * for additional information regarding copyright ownership.
 * Jasig licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.jasig.portal.events;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import javax.portlet.ActionRequest;
import javax.xml.namespace.QName;

import org.jasig.portal.concurrency.CallableWithoutResult;
import org.jasig.portal.concurrency.FunctionWithoutResult;
import org.jasig.portal.events.handlers.db.IPortalEventDao;
import org.jasig.portal.security.SystemPerson;
import org.jasig.portal.test.BaseJpaDaoTest;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

/**
 * @author Eric Dalquist
 * @version $Revision$
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = "classpath:jpaRawEventsTestApplicationContext.xml")
public class JpaPortalEventStoreTest extends BaseJpaDaoTest {
    @Autowired
    private IPortalEventDao portalEventDao;
    
    @Test
    public void testStoreSingleEvents() throws Exception {
        final List<PortalEvent> originalEvents = generateEvents();

        execute(new CallableWithoutResult() {
            @Override
            protected void callWithoutResult() {
                for (final PortalEvent event : originalEvents) {
                    portalEventDao.storePortalEvent(event);
                }
            }
        });
        
        verifyEvents(originalEvents);
    }
    
    @Test
    public void testStoreBatchEvents() throws Exception {
        final List<PortalEvent> originalEvents = generateEvents();
        
        execute(new CallableWithoutResult() {
            @Override
            protected void callWithoutResult() {
                final Date oldestPortalEventTimestamp = portalEventDao.getOldestPortalEventTimestamp();
                assertNull(oldestPortalEventTimestamp);
                
                final Date newestPortalEventTimestamp = portalEventDao.getNewestPortalEventTimestamp();
                assertNull(newestPortalEventTimestamp);
            }
        });
        

        execute(new CallableWithoutResult() {
            @Override
            protected void callWithoutResult() {
                portalEventDao.storePortalEvents(originalEvents);
            }
        });
        
        Collections.sort(originalEvents, new Comparator<PortalEvent>() {
            @Override
            public int compare(PortalEvent o1, PortalEvent o2) {
                return o1.getTimestampAsDate().compareTo(o2.getTimestampAsDate());
            }
        });
        
        execute(new CallableWithoutResult() {
            @Override
            protected void callWithoutResult() {
                final Date oldestPortalEventTimestamp = portalEventDao.getOldestPortalEventTimestamp();
                final Date newestPortalEventTimestamp = portalEventDao.getNewestPortalEventTimestamp();
                
                assertNotNull(oldestPortalEventTimestamp);
                assertNotNull(newestPortalEventTimestamp);
                
                assertEquals(originalEvents.get(0).getTimestampAsDate(), oldestPortalEventTimestamp);
                assertEquals(originalEvents.get(originalEvents.size() - 1).getTimestampAsDate(), newestPortalEventTimestamp);
            }
        });
        
        verifyEvents(originalEvents);
    }

    /**
     * @param originalEvents
     */
    protected void verifyEvents(final List<PortalEvent> originalEvents) {
        final Date startDate = new Date(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1));
        final Date endDate = new Date(System.currentTimeMillis() + TimeUnit.DAYS.toMillis(1));
        
        execute(new CallableWithoutResult() {
            @Override
            protected void callWithoutResult() {
                //Get all events
                final List<PortalEvent> portalEvents = new LinkedList<PortalEvent>();
                portalEventDao.getPortalEvents(startDate, endDate, new FunctionWithoutResult<PortalEvent>() {
                    @Override
                    protected void applyWithoutResult(PortalEvent input) {
                        portalEvents.add(input);
                    }
                });
                
                assertEquals(originalEvents.size(), portalEvents.size());
                
                final Iterator<PortalEvent> originalEventItr = originalEvents.iterator();
                final Iterator<PortalEvent> eventItr = portalEvents.iterator();
                
                while (originalEventItr.hasNext()) {
                    assertEquals(originalEventItr.next().getClass(), eventItr.next().getClass());
                }
                
                //Delete the events
                portalEventDao.deletePortalEventsBefore(endDate);
            }
        });
        
        execute(new CallableWithoutResult() {
            @Override
            protected void callWithoutResult() {
                final List<PortalEvent> portalEvents = new LinkedList<PortalEvent>();
                portalEventDao.getPortalEvents(startDate, endDate, new FunctionWithoutResult<PortalEvent>() {
                    @Override
                    protected void applyWithoutResult(PortalEvent input) {
                        portalEvents.add(input);
                    }
                });
                
                assertEquals(0, portalEvents.size());
            }
        });
    }
    
    private static final long EVENT_DELAY = 100;
    protected List<PortalEvent> generateEvents() throws Exception {
        final String sessionId = "1234567890123_system_AAAAAAAAAAA";
        final PortalEvent.PortalEventBuilder eventBuilder = new PortalEvent.PortalEventBuilder(this, "example.com", sessionId, SystemPerson.INSTANCE);
        
        final Set<String> groups = ImmutableSet.of("Student", "Employee");
        final Map<String, List<String>> attributes = ImmutableMap.of("username", (List<String>)ImmutableList.of("system"), "roles", (List<String>)ImmutableList.of("student", "employee"));

        
        final List<PortalEvent> events = new LinkedList<PortalEvent>();
        
        events.add(new LoginEvent(eventBuilder, groups, attributes));

        Thread.sleep(EVENT_DELAY);
        events.add(new FolderAddedToLayoutPortalEvent(eventBuilder, SystemPerson.INSTANCE, 1, "n32"));
        Thread.sleep(EVENT_DELAY);
        events.add(new FolderMovedInLayoutPortalEvent(eventBuilder, SystemPerson.INSTANCE, 1, "n12", "n32"));
        Thread.sleep(EVENT_DELAY);
        events.add(new FolderDeletedFromLayoutPortalEvent(eventBuilder, SystemPerson.INSTANCE, 1, "n24", "n32", "My Tab"));
        
        Thread.sleep(EVENT_DELAY);
        events.add(new PortletAddedToLayoutPortalEvent(eventBuilder, SystemPerson.INSTANCE, 1, "n32", "portletA"));
        Thread.sleep(EVENT_DELAY);
        events.add(new PortletMovedInLayoutPortalEvent(eventBuilder, SystemPerson.INSTANCE, 1, "n32", "n24", "portletA"));
        Thread.sleep(EVENT_DELAY);
        events.add(new PortletDeletedFromLayoutPortalEvent(eventBuilder, SystemPerson.INSTANCE, 1, "n24", "portletA"));
        
        
        Thread.sleep(EVENT_DELAY);
        events.add(new PortletActionExecutionEvent(eventBuilder, "portletA", 5, ImmutableMap.<String, List<String>>of(ActionRequest.ACTION_NAME, ImmutableList.of("foobar"))));
        Thread.sleep(EVENT_DELAY);
        events.add(new PortletEventExecutionEvent(eventBuilder, "portletA", 7, ImmutableMap.<String, List<String>>of(), new QName("http://www.jasig.org/foo", "event", "e")));
        Thread.sleep(EVENT_DELAY);
        events.add(new PortletRenderExecutionEvent(eventBuilder, "portletA", 13, ImmutableMap.<String, List<String>>of(), true, false));
        Thread.sleep(EVENT_DELAY);
        events.add(new PortletResourceExecutionEvent(eventBuilder, "portletA", 17, ImmutableMap.<String, List<String>>of(), "someImage.jpg", false));
        
        Thread.sleep(EVENT_DELAY);
        events.add(new LogoutEvent(eventBuilder));
        
        return events;
    }
}
