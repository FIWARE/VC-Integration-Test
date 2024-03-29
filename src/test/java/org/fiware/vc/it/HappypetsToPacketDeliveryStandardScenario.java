package org.fiware.vc.it;

import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import lombok.extern.slf4j.Slf4j;
import org.awaitility.Awaitility;
import org.fiware.vc.it.model.ApplicationConfig;
import org.fiware.vc.it.model.AuthResponseParams;
import org.fiware.vc.it.model.SameDeviceParams;
import org.fiware.vc.it.model.UserEnvironment;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Step-Implementations for standard scenarios, where:
 * - happypets issues credentials to the user
 * - the user-application initiates the authentication with PacketDelivery
 * - the application ends up with a JWT after Wallet and PacketDelivery fulfilled the OIDC4VP/SIOP-2 flow
 * - the application uses the JWT to access PacketDelveries ContextBroker
 */
@Slf4j
public class HappypetsToPacketDeliveryStandardScenario extends StepDefinitions {

	@Before
	public void setupEnvironment() {
		userEnvironment = new UserEnvironment();
	}

	@Given("An application provides access to PacketDelivery.")
	public void setupApplication() throws Exception {
		// even thought the application belongs to the "user-environment" in the test, the setup has it running in the `domain` of the provider
		// this could be any kind of service to allow participants accessing certain data or in a h2m-scenario a front-end application
		// allowing access to the provider's data(the frontend runs in the clients browser -> user environment)
		//
		// in the test scenario, this would be a service running for the provider, e.g PacketDelivery. Therefor, application
		// and verifier are available at the same host(e.g. same-device)
		userEnvironment.getApplication().setApplicationConfig(
				new ApplicationConfig(PacketDeliveryEnvironment.PACKET_DELIVERY_VERIFIER_ADDRESS,
						PacketDeliveryEnvironment.PACKET_DELIVERY_DID,
						PacketDeliveryEnvironment.PACKET_DELIVERY_VERIFIER_ADDRESS,
						"/token",
						PacketDeliveryEnvironment.PACKET_DELIVERY_ORION_ADDRESS,
						PacketDeliveryEnvironment.PACKET_DELIVERY_SERVICE_ID));
	}

	@Given("HappyPets is a trusted issuer in PacketDelivery.")
	public void registerHappyPetsAtPacketDelivery() throws Exception {
		// In order to be accepted by PacketDelivery, HappyPets needs to be registerd at the Trusted Issuers List of PacketDelivery.
		// In a real world use-case, that might happen through a marketplace, where HappyPets purchased the access for its users.
		userEnvironment.getApplication().registerTrustedIssuer(HappyPetsEnvironment.HAPPYPETS_DID);
	}

	@Given("The packet-delivery portal is registered at the config-service.")
	public void registerThePortal() throws Exception {
		// the portal has to be registered as a client at the verifier's config service
		userEnvironment.getApplication().registerCredentialsConfig();
		// we need to wait for the config service to get the credentials
		Thread.sleep(32000);
	}

	@When("The gold user requests a credentials offer from HappyPets.")
	public void happyPetsGoldGetCredentialsOffer() throws Exception {
		// a user account token is required to retrieve an offer
		// in a real world scenario, this could f.e. be a login to keycloak, where to QR is scanned
		// or any kind of m2m-interaction with Keycloak to get an service-account token
		String adminJwt = getUserTokenForAccountsAtHappypets(HappyPetsEnvironment.HAPPTYPETS_GOLD_USER,
				HappyPetsEnvironment.HAPPTYPETS_GOLD_USER_PASSWORD);

		// the token is used to receive a nonce from the keycloak-issuer, to be used to retrieve the actual offer
		userEnvironment.getWallet().getCredentialsOfferURI(adminJwt,
				String.format("%s/credential-offer-uri?type=PacketDeliveryService&format=ldp_vc",
						getHappyPetsIssuerBase()));
		assertTrue(userEnvironment.getWallet().getCredentialsOfferNonce().isPresent(),
				"The user's wallet should have received an offer nonce.");


		// the token is used to receive an actual offer from the keycloak-issuer
		userEnvironment.getWallet().getCredentialsOffer(adminJwt,
				String.format("%s/credential-offer/%s",
						getHappyPetsIssuerBase(),userEnvironment.getWallet().getCredentialsOfferNonce().get()));

		assertTrue(userEnvironment.getWallet().getCredentialsOffer().isPresent(),
				"The user's wallet should have received an offer.");
	}

	@When("The gold user requests a verifiable presentation offer from HappyPets.")
	public void happyPetsGoldGetVerifiablePresentationOffer() throws Exception {
		// a user account token is required to retrieve an offer
		// in a real world scenario, this could f.e. be a login to keycloak, where to QR is scanned
		// or any kind of m2m-interaction with Keycloak to get an service-account token
		String adminJwt = getUserTokenForAccountsAtHappypets(HappyPetsEnvironment.HAPPTYPETS_GOLD_USER,
				HappyPetsEnvironment.HAPPTYPETS_GOLD_USER_PASSWORD);

		// the token is used to receive a nonce from the keycloak-issuer, to be used to retrieve the actual offer
		userEnvironment.getWallet().getCredentialsOfferURI(adminJwt,
				String.format("%s/credential-offer-uri?type=PacketDeliveryService&format=ldp_vc",
						getHappyPetsIssuerBase()));
		assertTrue(userEnvironment.getWallet().getCredentialsOfferNonce().isPresent(),
				"The user's wallet should have received an offer nonce.");


		// the token is used to receive an actual offer from the keycloak-issuer
		userEnvironment.getWallet().getCredentialsOffer(adminJwt,
				String.format("%s/credential-offer/%s",
						getHappyPetsIssuerBase(),userEnvironment.getWallet().getCredentialsOfferNonce().get()));

		assertTrue(userEnvironment.getWallet().getCredentialsOffer().isPresent(),
				"The user's wallet should have received an offer.");
	}

	@When("The users uses the offer to receive a credential.")
	public void userGetsCredential() throws Exception {

		// the wallet should use the info from the offer, to receive the issuer's configuration
		userEnvironment.getWallet().getIssuerOpenIdConfiguration();
		assertTrue(userEnvironment.getWallet().getIssuerInfo().isPresent(),
				"The issuer information should have been retrieved by the wallet.");

		// the wallet needs to use the pre-authorized code from the offer to retrieve an access token.
		userEnvironment.getWallet().getTokenFromIssuer();
		assertTrue(userEnvironment.getWallet().getAccessToken().isPresent(),
				"The wallet should have received an access token.");

		// the wallet will use the access token, the openId-info and the offer to retrieve the actual credential
		userEnvironment.getWallet().getTheCredential();
		assertTrue(userEnvironment.getWallet().getCredential().isPresent(),
				"The wallet should have received a credential.");
	}

	@When("The user authenticates with the same-device flow.")
	public void authenticateViaSameDeviceFlow() throws Exception {

		// the application is called and initiates a login-session(f.e. a frontend would forward to the login page)
		userEnvironment.getApplication().initiateLogin();
		assertTrue(userEnvironment.getApplication().getLoginSession().isPresent(),
				"A login session should have been started.");

		// since we are testing the same-device flow, the application will initiate that flow, to be continued by the wallet
		// in a frontend application, this would forward to the login page of the verifier, to get a scanable qr
		// the same device flow expects a redirect, that should be handled by the wallet. In the test, we dont follow the redirect
		// but instead capture the response and hand it over to the wallet "manually"
		SameDeviceParams sameDeviceParams = userEnvironment.getApplication().startSameDeviceFlow();
		assertNotNull(sameDeviceParams,
				"A redirect with the parameters for the same device flow should have been returned.");

		// the wallet on the same device will handle the redirect and continue the authorization flow. It will also expect a 302 that will
		// hand over the flow to the application(to continue the actual jwt retrieval) again
		AuthResponseParams authResponseParams = userEnvironment.getWallet().answerAuthRequest(sameDeviceParams);
		assertNotNull(authResponseParams,
				"The parameters to be used for the actual token retrieval should have been returned.");

		// the application will receive the redirect and handle the parameters accordingly, e.g. exchange the auth_token
		// through the token endpoint for the JWT.
		userEnvironment.getApplication().exchangeCodeForJWT(authResponseParams);
		assertTrue(userEnvironment.getApplication().getJwt().isPresent(), "A JWT should have been retrieved.");

	}

	@When("The user authenticates with the M2M flow.")
	public void authenticateViaM2MFlow() throws Exception {

		userEnvironment.getApplication().startM2MFlow(userEnvironment.getWallet());
		assertTrue(userEnvironment.getApplication().getJwt().isPresent(), "A JWT should have been retrieved via M2M Flow.");

	}

	@Then("The user can access PacketDeliveries backend.")
	public void userAccessPdc() throws Exception {
		// the jwt will now be used to access the user backend
		userEnvironment.getApplication().accessBackend();
	}

}