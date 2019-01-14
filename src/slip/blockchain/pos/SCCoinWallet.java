package slip.blockchain.pos;

import java.security.KeyPair;

import slip.security.common.RSA;
import slip.security.common.RSAKey;

public class SCCoinWallet {
	
	private String privateKeyAsString; // secrète, est la clef du coffre de l'utilisateur
	private String publicKeyAsString;
	
	public static SCCoinWallet createNewWallet()  {
		KeyPair keys = null;
		try {
			keys = RSA.generateRSAKeyPair();
		} catch (Exception e) { }
		if (keys == null) return null;

		String privateKeyAsString = RSAKey.saveKey(keys.getPrivate());
		String prublicKeyAsString = RSAKey.saveKey(keys.getPublic());
		return new SCCoinWallet(privateKeyAsString, prublicKeyAsString);
		
	}
	
	public SCCoinWallet(String arg_privateKey, String arg_publicKey) {
		privateKeyAsString = arg_privateKey;
		publicKeyAsString = arg_publicKey;
		
	}
	
}