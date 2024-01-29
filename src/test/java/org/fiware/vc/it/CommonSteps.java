package org.fiware.vc.it;

import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.When;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpStatus;
import org.fiware.vc.it.model.UserEnvironment;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

@Slf4j
public class CommonSteps extends StepDefinitions {

	@Before
	public void setupEnvironment() {
		userEnvironment = new UserEnvironment();
	}

	@Given("The HappyPets issuer is ready to provide credentials.")
	public void happyPetsIsAvailable() throws Exception {
		HttpRequest wellKnownRequest = HttpRequest.newBuilder()
				.uri(URI.create(
						String.format("%s/.well-known/openid-credential-issuer",
								getHappyPetsIssuerBase())))
				.build();

		await().atMost(Duration.of(1, ChronoUnit.MINUTES))
				.until(() -> HTTP_CLIENT.send(wellKnownRequest, HttpResponse.BodyHandlers.ofString())
						.statusCode() == 200);

		// this assures that the realm is successfully imported
		await().atMost(Duration.of(2, ChronoUnit.MINUTES))
				.until(() -> {
					try {
						getUserTokenForAccountsAtHappypets(HappyPetsEnvironment.HAPPTYPETS_GOLD_USER,
								HappyPetsEnvironment.HAPPTYPETS_GOLD_USER_PASSWORD);
						return true;
					} catch (Exception e) {
						log.warn("Setup not finished.", e);
						return false;
					}
				});
	}
}
