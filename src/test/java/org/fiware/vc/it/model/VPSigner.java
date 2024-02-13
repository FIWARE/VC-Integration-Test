package org.fiware.vc.it.model;

import com.apicatalog.jsonld.JsonLd;
import com.apicatalog.jsonld.JsonLdError;
import com.apicatalog.jsonld.document.JsonDocument;
import com.apicatalog.jsonld.http.DefaultHttpClient;
import com.apicatalog.jsonld.http.media.MediaType;
import com.apicatalog.jsonld.json.JsonUtils;
import com.apicatalog.jsonld.loader.HttpLoader;
import com.apicatalog.rdf.Rdf;
import com.apicatalog.rdf.RdfDataset;
import com.apicatalog.rdf.io.RdfWriter;
import com.apicatalog.rdf.io.error.RdfWriterException;
import com.apicatalog.rdf.io.error.UnsupportedContentException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nimbusds.jose.util.Base64URL;
import io.setl.rdf.normalization.RdfNormalize;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import lombok.RequiredArgsConstructor;
import org.bouncycastle.crypto.params.AsymmetricKeyParameter;
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.bouncycastle.crypto.signers.Ed25519Signer;
import org.keycloak.common.util.Base64;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RequiredArgsConstructor
public class VPSigner {

	private final ObjectMapper objectMapper;

	private byte[] prepareKey() {

		/**
		 * 	  Taken from the keycloak deployment
		 *    {
		 *       "kty": "OKP",
		 *       "d": "gGqnb6ij2s-BGUpAgWVGv9odIHciuF-GuLDqyt2pLm0",
		 *       "use": "sig",
		 *       "crv": "Ed25519",
		 *       "kid": "z6MkigCEnopwujz8Ten2dzq91nvMjqbKQYcifuZhqBsEkH7g",
		 *       "x": "Pr7-Zath5ZMyvikX93LzsJNkl6HcAAJdgrwlBTSE6gs",
		 *       "alg": "EdDSA"
		 *     }
		 */
		return new Base64URL("nWGxne_9WmC6hEr0kuwsxERJxWl7MmkZcDusAxyuf2A").decode();
	}

	public VerifiablePresentation createPresentationFromVC(ObjectNode vc) {
		VerifiablePresentation vp = new VerifiablePresentation();
		vp.setVerifiableCredential(List.of(vc));
		vp.setId(UUID.randomUUID().toString());
		vp.setHolder(vc.get("issuer").asText());
		return signVP(vp);
	}

	private VerifiablePresentation signVP(VerifiablePresentation vp) {
		byte[] expandedCredential = expand(vp);
		byte[] hash = digest(expandedCredential);
		byte[] signature = sign(hash, prepareKey());
		LdProof ldProof = new LdProof();
		ldProof.setProofPurpose("assertionMethod");
		ldProof.setType("Ed25519Signature2018");
		ldProof.setCreated(Date.from(Instant.now()));
		ldProof.setVerificationMethod("z6MkigCEnopwujz8Ten2dzq91nvMjqbKQYcifuZhqBsEkH7g");

		try {
			var proofValue = Base64.encodeBytes(signature, Base64.URL_SAFE);
			ldProof.setProofValue(proofValue);
			vp.setProof(JsonUtils.toJsonValue(objectMapper.writeValueAsString(ldProof)));
			return vp;
		} catch (IOException e) {
			throw new IllegalStateException("Was not able to encode the signature.", e);
		}
	}

	private byte[] expand(Object credential) {
		try {
			String credentialString = objectMapper.writeValueAsString(credential);

			var credentialDocument = JsonDocument.of(new StringReader(credentialString));

			var expandedDocument = JsonLd.expand(credentialDocument)
					.loader(new HttpLoader(DefaultHttpClient.defaultInstance()))
					.get();
			Optional<JsonObject> documentObject = Optional.empty();
			if (JsonUtils.isArray(expandedDocument)) {
				documentObject = expandedDocument.asJsonArray().stream().filter(JsonUtils::isObject).map(JsonValue::asJsonObject).findFirst();
			} else if (JsonUtils.isObject(expandedDocument)) {
				documentObject = Optional.of(expandedDocument.asJsonObject());
			}
			if (documentObject.isPresent()) {

				RdfDataset rdfDataset = JsonLd.toRdf(JsonDocument.of(documentObject.get())).get();
				RdfDataset canonicalDataset = RdfNormalize.normalize(rdfDataset);

				StringWriter writer = new StringWriter();
				RdfWriter rdfWriter = Rdf.createWriter(MediaType.N_QUADS, writer);
				rdfWriter.write(canonicalDataset);

				return writer.toString()
						.getBytes(StandardCharsets.UTF_8);
			} else {
				throw new IllegalStateException("Was not able to get the expanded json.");
			}
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("Was not able to serialize the credential", e);
		} catch (JsonLdError e) {
			throw new IllegalStateException("Was not able to create a JsonLD Document from the serialized string.", e);
		} catch (UnsupportedContentException | IOException | RdfWriterException e) {
			throw new IllegalStateException("Was not able to canonicalize the json-ld.", e);
		}
	}

	private byte[] digest(byte[] transformedData) {
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-256");
			return md.digest(transformedData);
		} catch (NoSuchAlgorithmException e) {
			throw new RuntimeException(e);
		}
	}

	private byte[] sign(byte[] hashData, byte[] key) {
		Ed25519Signer signer = new Ed25519Signer();
		signer.init(true, toKeyParameter(key));
		signer.update(hashData, 0, hashData.length);
		return signer.generateSignature();
	}


	private static AsymmetricKeyParameter toKeyParameter(byte[] key) {
		try {
			return new Ed25519PrivateKeyParameters(key);
		} catch (Exception e) {
			throw new IllegalStateException("Was not able to get the private key parameter.", e);
		}

	}

}
