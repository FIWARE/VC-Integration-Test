package org.fiware.vc.it;

public class HappyPetsEnvironment {

	public static String PACKET_DELIVERY_DID = "did:key:z6MksU6tMfbaDzvaRe5oFE4eZTVTV4HJM4fmQWWGsDGQVsEr";
	public static String HAPPYPETS_DID = "did:key:z6MkigCEnopwujz8Ten2dzq91nvMjqbKQYcifuZhqBsEkH7g";
	public static String HAPPYPETS_EORI = "EU.EORI.HAPPYPETS";
	public static String HAPPTYPETS_ADMIN_USER = "fiwareAdmin";
	public static String HAPPTYPETS_ADMIN_PASSWORD = "fiwareAdmin";
	public static String HAPPTYPETS_STANDARD_USER = "standard-user";
	public static String HAPPTYPETS_STANDARD_USER_PASSWORD = "standard-user";
	public static String HAPPTYPETS_GOLD_USER = "prime-user";
	public static String HAPPTYPETS_GOLD_USER_PASSWORD = "prime-user";
	// currently not verified, thus does not need to be a real one
	public static String HAPPYPETS_ADMIN_DID = "did:my:admin";
	public static String HAPPYPETS_GOLD_USER_DID = "did:user:gold";
	public static String HAPPYPETS_STANDARD_USER_DID = "did:user:standard";

	// the keycloak
	public static String HAPPYPETS_ISSUER_ADDRESS = "http://localhost:8080/";
	public static String HAPPYPETS_ISSUER_REALM = "fiware-server";

}
