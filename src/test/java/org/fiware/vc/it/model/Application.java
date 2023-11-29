package org.fiware.vc.it.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.fiware.ccs.model.CredentialVO;
import org.fiware.ccs.model.ServiceVO;
import org.fiware.til.model.ClaimVO;
import org.fiware.til.model.CredentialsVO;
import org.fiware.til.model.TrustedIssuerVO;
import org.fiware.vc.it.PacketDeliveryEnvironment;

import javax.ws.rs.core.MediaType;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.fiware.vc.it.TestUtils.getFormDataAsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Slf4j
public class Application {

	// even thought its best practice to just use one ObjectMapper per JVM, we accept the performance impact for the tests
	// to improve readability and test isolation.
	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	private static final HttpClient HTTP_CLIENT = HttpClient
			.newBuilder()
			// we don't follow the redirect directly, since we are not a real wallet
			.followRedirects(HttpClient.Redirect.NEVER)
			.build();

	@Getter
	@Setter
	private ApplicationConfig applicationConfig;

	private String loginSession;
	private String jwt;

	public void initiateLogin() {
		loginSession = UUID.randomUUID().toString();
	}

	public void registerCredentialsConfig() throws Exception {
		ServiceVO pdcPortalService = new ServiceVO()
				.id(PacketDeliveryEnvironment.PACKET_DELIVERY_SERVICE_ID)
				.credentials(List.of(
						new CredentialVO().type("VerifiableCredential")
								.trustedIssuersLists(List.of("http://til:5050/"))
								.trustedParticipantsLists(List.of("http://til:5050/")
								),
						new CredentialVO().type("PacketDeliveryService")
								.trustedIssuersLists(List.of("http://til:5050/"))
								.trustedParticipantsLists(List.of("http://til:5050/"))
				));
		HttpRequest registerService = HttpRequest.newBuilder()
				.uri(URI.create(String.format("%s/service",
						PacketDeliveryEnvironment.PACKET_DELIVERY_CREDENTIALS_CONFIG_SERVICE_ADDRESS)))
				.POST(HttpRequest.BodyPublishers.ofString(OBJECT_MAPPER.writeValueAsString(pdcPortalService)))
				.header("Content-Type", "application/json")
				.build();
		int creationState = HTTP_CLIENT.send(registerService, HttpResponse.BodyHandlers.ofString()).statusCode();

		assertTrue(List.of(HttpStatus.SC_CREATED, HttpStatus.SC_CONFLICT).contains(creationState),
				"The service should either be created or already exist.");
	}

	public void registerTrustedIssuer(String did) throws Exception {

		List<Object> allowedRoles = List.of(
				// the current implementation of the verifier requires an exact match of the claim, thus every potential combination needs to be registered
				List.of(new AllowedRoles().setNames(List.of("GOLD_CUSTOMER", "STANDARD_CUSTOMER"))
						.setTarget(PacketDeliveryEnvironment.PACKET_DELIVERY_DID)),
				List.of(new AllowedRoles().setNames(List.of("STANDARD_CUSTOMER", "GOLD_CUSTOMER"))
						.setTarget(PacketDeliveryEnvironment.PACKET_DELIVERY_DID)),
				List.of(new AllowedRoles().setNames(List.of("GOLD_CUSTOMER"))
						.setTarget(PacketDeliveryEnvironment.PACKET_DELIVERY_DID)),
				List.of(new AllowedRoles().setNames(List.of("STANDARD_CUSTOMER"))
						.setTarget(PacketDeliveryEnvironment.PACKET_DELIVERY_DID))
		);

		TrustedIssuerVO issuerToRegister = new TrustedIssuerVO()
				.did(did)
				.credentials(List.of(
						new CredentialsVO()
								.credentialsType("VerifiableCredential")
								.claims(
										List.of(
												new ClaimVO()
														.name("roles")
														.allowedValues(allowedRoles))),
						new CredentialsVO()
								.credentialsType("PacketDeliveryService")
								.claims(
										List.of(
												new ClaimVO()
														.name("roles")
														.allowedValues(allowedRoles)))

				));
		HttpRequest registerIssuer = HttpRequest.newBuilder()
				.uri(URI.create(String.format("%s/issuer",
						PacketDeliveryEnvironment.PACKET_DELIVERY_TRUSTED_ISSUERS_LIST_ADDRESS)))
				.POST(HttpRequest.BodyPublishers.ofString(OBJECT_MAPPER.writeValueAsString(issuerToRegister)))
				.header("Content-Type", "application/json")
				.build();
		int creationState = HTTP_CLIENT.send(registerIssuer, HttpResponse.BodyHandlers.ofString()).statusCode();

		assertTrue(List.of(HttpStatus.SC_CREATED, HttpStatus.SC_CONFLICT).contains(creationState),
				"The issuer should either be created or already exist.");
	}

	public SameDeviceParams startSameDeviceFlow() throws Exception {
		HttpRequest startSameDevice = HttpRequest.newBuilder()
				.uri(URI.create(String.format("%s/api/v1/samedevice?state=%s&client_id=%s",
						applicationConfig.verifierAddress(), loginSession, applicationConfig.clientId())))
				.GET()
				.build();
		HttpResponse<String> sameDeviceResponse = HTTP_CLIENT.send(startSameDevice,
				HttpResponse.BodyHandlers.ofString());

		assertEquals(302, sameDeviceResponse.statusCode(), "We should receive a redirect.");

		String locationHeader = sameDeviceResponse.headers().firstValue("location").get();
		List<NameValuePair> params = URLEncodedUtils.parse(URI.create(locationHeader), Charset.forName("UTF-8"));
		SameDeviceParams sameDeviceParams = new SameDeviceParams();
		params.forEach(p -> {
			switch (p.getName()) {
				case "response_type" -> sameDeviceParams.setResponseType(p.getValue());
				case "response_mode" -> sameDeviceParams.setResponseMode(p.getValue());
				case "client_id" -> sameDeviceParams.setClientId(p.getValue());
				case "redirect_uri" -> sameDeviceParams.setRedirectUri(p.getValue());
				case "state" -> sameDeviceParams.setState(p.getValue());
				case "nonce" -> sameDeviceParams.setNonce(p.getValue());
				case "scope" -> sameDeviceParams.setScope(p.getValue());
				default -> log.warn("Received an unknown parameter: {}", p.getName());
			}
		});

		assertEquals("vp_token", sameDeviceParams.getResponseType(), "Currently, only vp_token is supported.");
		assertEquals("direct_post", sameDeviceParams.getResponseMode(), "Currently, only direct_post is supported.");
		assertEquals(applicationConfig.verifierDid(), sameDeviceParams.getClientId(),
				"The expected participant should have initiated the flow.");
		assertNotNull(sameDeviceParams.getRedirectUri(), "A redirect_uri should have been received.");
		assertNotNull(sameDeviceParams.getState(), "The verifier should have creadet a state.");

		return sameDeviceParams;
	}

	public void exchangeCodeForJWT(AuthResponseParams authResponseParams) throws Exception {
		Map<String, String> tokenRequestFormData = Map.of(

				"grant_type", "authorization_code",
				"code", authResponseParams.getCode(),
				// we did not set a redirect_path, thus in same device we will end up where it began.
				"redirect_uri", applicationConfig.applicationUrl() + "/");

		HttpRequest jwtRequest = HttpRequest.newBuilder()
				.uri(URI.create(
						String.format("%s%s", applicationConfig.verifierAddress(), applicationConfig.tokenPath())))
				.POST(HttpRequest.BodyPublishers.ofString(getFormDataAsString(tokenRequestFormData)))
				.header("Content-Type", MediaType.APPLICATION_FORM_URLENCODED)
				.build();
		HttpResponse<String> tokenResponse = HTTP_CLIENT.send(jwtRequest, HttpResponse.BodyHandlers.ofString());
		assertEquals(HttpStatus.SC_OK, tokenResponse.statusCode(), "A token should have been returned.");
		TokenResponse tr = OBJECT_MAPPER.readValue(tokenResponse.body(), TokenResponse.class);

		jwt = tr.getAccessToken();
	}

	public void accessBackend() throws Exception {

		HttpRequest orionRequest = HttpRequest.newBuilder()
				.uri(URI.create(String.format("%s/ngsi-ld/v1/entities?type=DELIVERYORDER",
						applicationConfig.securedBackend())))
				.GET()
				.header("Authorization", "Bearer " + jwt)
				.build();
		HttpResponse<String> orionResponse = HTTP_CLIENT.send(orionRequest, HttpResponse.BodyHandlers.ofString());
		assertEquals(HttpStatus.SC_OK, orionResponse.statusCode(), "The request should have been allowed.");
	}

	public Optional<String> getLoginSession() {
		return Optional.ofNullable(loginSession);
	}

	public Optional<String> getJwt() {
		return Optional.ofNullable(jwt);
	}

}
