/*
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.mobicents.servlet.sip.testsuite.security;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

import javax.sip.SipProvider;
import javax.sip.header.Header;
import javax.sip.header.ProxyAuthenticateHeader;
import javax.sip.header.ProxyAuthorizationHeader;
import javax.sip.message.Response;

import org.apache.catalina.deploy.ApplicationParameter;
import org.apache.log4j.Logger;
import org.mobicents.servlet.sip.SipServletTestCase;
import org.mobicents.servlet.sip.core.session.SipStandardManager;
import org.mobicents.servlet.sip.startup.SipContextConfig;
import org.mobicents.servlet.sip.startup.SipStandardContext;
import org.mobicents.servlet.sip.testsuite.ProtocolObjects;
import org.mobicents.servlet.sip.testsuite.TestSipListener;

public class ShootistSipServletAuthTest extends SipServletTestCase {
	private static transient Logger logger = Logger.getLogger(ShootistSipServletAuthTest.class);		
	private static final String TRANSPORT = "udp";
	private static final boolean AUTODIALOG = true;
	private static final int TIMEOUT = 30000;	
	private static final int TIME_TO_WAIT_BETWEEN_PROV_RESPONSES = 7000;
//	private static final int TIMEOUT = 100000000;
	
	TestSipListener receiver;
	
	ProtocolObjects receiverProtocolObjects;
	
	public ShootistSipServletAuthTest(String name) {
		super(name);
		startTomcatOnStartup = false;
		autoDeployOnStartup = false;
	}

	@Override
	public void deployApplication() {
		assertTrue(tomcat.deployContext(
				projectHome + "/sip-servlets-test-suite/applications/shootist-sip-servlet-auth/src/main/sipapp",
				"sip-test-context", "sip-test"));
	}
	
	public SipStandardContext deployApplication(String name, String value) {
		SipStandardContext context = new SipStandardContext();
		context.setDocBase(projectHome + "/sip-servlets-test-suite/applications/shootist-sip-servlet-auth/src/main/sipapp");
		context.setName("sip-test-context");
		context.setPath("sip-test");
		context.addLifecycleListener(new SipContextConfig());
		context.setManager(new SipStandardManager());
		ApplicationParameter applicationParameter = new ApplicationParameter();
		applicationParameter.setName(name);
		applicationParameter.setValue(value);
		context.addApplicationParameter(applicationParameter);
		assertTrue(tomcat.deployContext(context));
		return context;
	}

	@Override
	protected String getDarConfigurationFile() {
		return "file:///" + projectHome + "/sip-servlets-test-suite/testsuite/src/test/resources/" +
				"org/mobicents/servlet/sip/testsuite/security/shootist-sip-servlet-auth-dar.properties";
	}
	
	@Override
	protected void setUp() throws Exception {
		super.setUp();									
	}
	
	public void testShootist() throws Exception {
//		receiver.sendInvite();
		receiverProtocolObjects =new ProtocolObjects(
				"sender", "gov.nist", TRANSPORT, AUTODIALOG, null);
					
		receiver = new TestSipListener(5080, 5070, receiverProtocolObjects, false);
		receiver.setChallengeRequests(true);
		List<Integer> provisionalResponsesToSend = new ArrayList<Integer>();
		provisionalResponsesToSend.add(Response.TRYING);
		provisionalResponsesToSend.add(Response.SESSION_PROGRESS);		
		receiver.setProvisionalResponsesToSend(provisionalResponsesToSend);
		receiver.setTimeToWaitBetweenProvisionnalResponse(TIME_TO_WAIT_BETWEEN_PROV_RESPONSES);
		SipProvider senderProvider = receiver.createProvider();			
		
		senderProvider.addSipListener(receiver);
		
		receiverProtocolObjects.start();		
		
		tomcat.startTomcat();
		deployApplication();
		Thread.sleep(TIMEOUT);
		assertTrue(receiver.isAckReceived());
		assertTrue(receiver.getByeReceived());
	}
	/*
	 * Non regression test for Issue 1832 : http://code.google.com/p/mobicents/issues/detail?id=1832 
     * Authorization header is growing when nonce become stale
     */
	public void testShootistReinviteChallengeStale() throws Exception {
//		receiver.sendInvite();
		receiverProtocolObjects =new ProtocolObjects(
				"sender-app-send-reinvite", "gov.nist", TRANSPORT, AUTODIALOG, null);
					
		receiver = new TestSipListener(5080, 5070, receiverProtocolObjects, false);
		receiver.setChallengeRequests(true);
		receiver.setMultipleChallengeInResponse(true);
		List<Integer> provisionalResponsesToSend = new ArrayList<Integer>();
		provisionalResponsesToSend.add(Response.TRYING);
		provisionalResponsesToSend.add(Response.SESSION_PROGRESS);		
		receiver.setProvisionalResponsesToSend(provisionalResponsesToSend);
		receiver.setTimeToWaitBetweenProvisionnalResponse(TIME_TO_WAIT_BETWEEN_PROV_RESPONSES);
		SipProvider senderProvider = receiver.createProvider();			
		
		senderProvider.addSipListener(receiver);
		
		receiverProtocolObjects.start();		
		
		tomcat.startTomcat();
		deployApplication();
		Thread.sleep(TIMEOUT);
		assertTrue(receiver.isAckReceived());
		assertTrue(receiver.getByeReceived());
		ListIterator<Header>  proxyAuthHeaders = receiver.getInviteRequest().getHeaders(ProxyAuthorizationHeader.NAME);
		int i= 0; 
		while (proxyAuthHeaders.hasNext()) {
			proxyAuthHeaders.next();
			i++;			
		}
		assertEquals("The stale auth header should not be taken into account", 1, i);
	}
	
	/*
	 * Non regression test for Issue 1836 
	 * http://code.google.com/p/mobicents/issues/detail?id=1836
	 * Exception thrown when creating a cancel after a "Proxy Authentication required" response
	 */
	public void testShootistCancelChallengeOn1xx() throws Exception {
		receiverProtocolObjects =new ProtocolObjects(
				"cancelChallenge", "gov.nist", TRANSPORT, AUTODIALOG, null);
					
		receiver = new TestSipListener(5080, 5070, receiverProtocolObjects, false);
		receiver.setChallengeRequests(true);
		receiver.setWaitForCancel(true);
		List<Integer> provisionalResponsesToSend = new ArrayList<Integer>();
		provisionalResponsesToSend.add(Response.TRYING);
		provisionalResponsesToSend.add(Response.SESSION_PROGRESS);		
		receiver.setProvisionalResponsesToSend(provisionalResponsesToSend);
		receiver.setTimeToWaitBetweenProvisionnalResponse(TIME_TO_WAIT_BETWEEN_PROV_RESPONSES);
		SipProvider receiverProvider = receiver.createProvider();			
		
		receiverProvider.addSipListener(receiver);
		
		receiverProtocolObjects.start();		
		
		tomcat.startTomcat();
		deployApplication("from", "cancelChallenge");
		Thread.sleep(TIMEOUT);
		assertTrue(receiver.isCancelReceived());			
	}
	
	/*
	 * Non regression test for Issue 1836 
	 * http://code.google.com/p/mobicents/issues/detail?id=1836
	 * Exception thrown when creating a cancel after a "Proxy Authentication required" response
	 */
	public void testShootistCancelChallengeBefore1xx() throws Exception {
		receiverProtocolObjects =new ProtocolObjects(
				"cancelChallengeBefore1xx", "gov.nist", TRANSPORT, AUTODIALOG, null);
					
		receiver = new TestSipListener(5080, 5070, receiverProtocolObjects, false);
		receiver.setChallengeRequests(true);
		receiver.setWaitForCancel(true);
		List<Integer> provisionalResponsesToSend = new ArrayList<Integer>();
		provisionalResponsesToSend.add(Response.TRYING);
		provisionalResponsesToSend.add(Response.SESSION_PROGRESS);		
		receiver.setProvisionalResponsesToSend(provisionalResponsesToSend);
		receiver.setTimeToWaitBetweenProvisionnalResponse(TIME_TO_WAIT_BETWEEN_PROV_RESPONSES);
		SipProvider receiverProvider = receiver.createProvider();			
		
		receiverProvider.addSipListener(receiver);
		
		receiverProtocolObjects.start();		
		
		tomcat.startTomcat();
		deployApplication("from", "cancelChallengeBefore1xx");
		Thread.sleep(TIMEOUT);
		assertTrue(receiver.isCancelReceived());			
	}
	
	/*
	 * Non regression test for Issue 1836 
	 * http://code.google.com/p/mobicents/issues/detail?id=1836
	 * Exception thrown when creating a cancel after a "Proxy Authentication required" response
	 */
	public void testShootistReInviteCancel() throws Exception {
		receiverProtocolObjects =new ProtocolObjects(
				"reinvite", "gov.nist", TRANSPORT, AUTODIALOG, null);
					
		receiver = new TestSipListener(5080, 5070, receiverProtocolObjects, false);
		receiver.setChallengeRequests(true);
		List<Integer> provisionalResponsesToSend = new ArrayList<Integer>();
		provisionalResponsesToSend.add(Response.TRYING);
		provisionalResponsesToSend.add(Response.SESSION_PROGRESS);		
		receiver.setProvisionalResponsesToSend(provisionalResponsesToSend);
		receiver.setTimeToWaitBetweenProvisionnalResponse(TIME_TO_WAIT_BETWEEN_PROV_RESPONSES);
		SipProvider receiverProvider = receiver.createProvider();			
		
		receiverProvider.addSipListener(receiver);
		
		receiverProtocolObjects.start();		
		
		tomcat.startTomcat();
		deployApplication("from", "reinvite");
		Thread.sleep(TIMEOUT);
		assertTrue(receiver.isAckReceived());		
		receiver.setWaitForCancel(true);
		receiver.setChallengeRequests(false);
		receiver.sendInDialogSipRequest("INFO", null, null, null, null, null);
		Thread.sleep(TIMEOUT);
		assertTrue(receiver.isCancelReceived());		
	}

	@Override
	protected void tearDown() throws Exception {					
		receiverProtocolObjects.destroy();			
		logger.info("Test completed");
		super.tearDown();
	}
}