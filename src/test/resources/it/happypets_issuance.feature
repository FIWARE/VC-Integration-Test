Feature: An Happypets User get a credentials and uses it to access packetdelivery.

  Scenario: An admin user gets a credential from HappyPets, using the OIDC4VCI flow.
    Given An application provides access to PacketDelivery.
    And The packet-delivery portal is registered at the config-service.
    And HappyPets is a trusted issuer in PacketDelivery.
    And The HappyPets issuer is ready to provide credentials.
    When The gold user requests a credentials offer from HappyPets.
    And The users uses the offer to receive a credential.
    And The user authenticates with the same-device flow.
    Then The user can access PacketDeliveries backend.

  Scenario: An admin user gets a credential from HappyPets, using the OIDC4VCI flow and accesses the service via M2M token.
    Given An application provides access to PacketDelivery.
    And The packet-delivery portal is registered at the config-service.
    And HappyPets is a trusted issuer in PacketDelivery.
    And The HappyPets issuer is ready to provide credentials.
    When The gold user requests a verifiable presentation offer from HappyPets.
    And The users uses the offer to receive a credential.
    And The user authenticates with the M2M flow.
    Then The user can access PacketDeliveries backend.