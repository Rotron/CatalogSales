/* Based on:
 * Title: AdvancedCrypto
 * Author: stackoverflow user Light Flow - https://stackoverflow.com/users/2335593/light-flow
 * Date: 21/08/2017
 * Availability:
 * https://stackoverflow.com/questions/16299042/custom-encryption-class-for-android-runtime-error
 * Modification of the https://stackoverflow.com/questions/8622367/
 * what-are-best-practices-for-using-aes-encryption-in-android/8694307#8694307
 * from stackoverflow user caw - https://stackoverflow.com/users/89818/caw
 */

package com.humaneapps.catalogsales.helper;

import android.support.annotation.NonNull;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Helper class for encrypting credentials. Used to encrypt dropbox token.
 */

public class AdvancedCrypto {


    private static final String PROVIDER = "BC";
    private static final int SALT_LENGTH = 20;
    private static final int IV_LENGTH = 16;
    private static final int PBE_ITERATION_COUNT = 100;

    private static final String RANDOM_ALGORITHM = "SHA1PRNG";
    private static final String HASH_ALGORITHM = "SHA-512";
    private static final String PBE_ALGORITHM = "PBEWithSHA256And256BitAES-CBC-BC";
    private static final String CIPHER_ALGORITHM = "AES/CBC/PKCS5Padding";
    private static final String SECRET_KEY_ALGORITHM = "AES";


    private static byte[] toByte(String hexString) {
        int len = hexString.length() / 2;
        byte[] result = new byte[len];
        for (int i = 0; i < len; i++)
            result[i] = Integer.valueOf(hexString.substring(2 * i, 2 * i + 2), 16).byteValue();
        return result;
    }


    @NonNull
    private static String toHex(byte[] bufs) {
        if (bufs == null) { return ""; }
        StringBuffer result = new StringBuffer(2 * bufs.length);
        for (byte buf : bufs) {
            appendHex(result, buf);
        }
        return result.toString();
    }


    private final static String HEX = "0123456789ABCDEF";


    private static void appendHex(StringBuffer sb, byte b) {
        sb.append(HEX.charAt((b >> 4) & 0x0f)).append(HEX.charAt(b & 0x0f));
    }


    public static String encrypt(SecretKey secret, String cleartext) throws Exception {
        try {

            byte[] iv = generateIv();
            String ivHex = toHex(iv);
            IvParameterSpec ivspec = new IvParameterSpec(iv);

            Cipher encryptionCipher = Cipher.getInstance(CIPHER_ALGORITHM, PROVIDER);
            encryptionCipher.init(Cipher.ENCRYPT_MODE, secret, ivspec);
            byte[] encryptedText = encryptionCipher.doFinal(cleartext.getBytes("UTF-8"));
            String encryptedHex = toHex(encryptedText);

            return ivHex + encryptedHex;

        } catch (Exception e) {
            throw new Exception("Unable to encrypt", e);
        }
    }


    public static String decrypt(SecretKey secret, String encrypted) throws Exception {
        try {
            Cipher decryptionCipher = Cipher.getInstance(CIPHER_ALGORITHM, PROVIDER);
            String ivHex = encrypted.substring(0, IV_LENGTH * 2);
            String encryptedHex = encrypted.substring(IV_LENGTH * 2);
            IvParameterSpec ivspec = new IvParameterSpec(toByte(ivHex));
            decryptionCipher.init(Cipher.DECRYPT_MODE, secret, ivspec);
            byte[] decryptedText = decryptionCipher.doFinal(toByte(encryptedHex));
            return new String(decryptedText, "UTF-8");
        } catch (Exception e) {
            throw new Exception("Unable to decrypt", e);
        }
    }


    public static SecretKey getSecretKey(String password, String salt) throws Exception {
        try {
            PBEKeySpec pbeKeySpec = new PBEKeySpec(password.toCharArray(), toByte(salt),
                    PBE_ITERATION_COUNT, 256);
            SecretKeyFactory factory = SecretKeyFactory.getInstance(PBE_ALGORITHM, PROVIDER);
            SecretKey tmp = factory.generateSecret(pbeKeySpec);
            return new SecretKeySpec(tmp.getEncoded(), SECRET_KEY_ALGORITHM);
        } catch (Exception e) {
            throw new Exception("Unable to get secret key", e);
        }
    }


    @SuppressWarnings("unused")
    public String getHash(String password, String salt) throws Exception {
        try {
            String input = password + salt;
            MessageDigest md = MessageDigest.getInstance(HASH_ALGORITHM, PROVIDER);
            byte[] out = md.digest(input.getBytes("UTF-8"));
            return toHex(out);
        } catch (Exception e) {
            throw new Exception("Unable to get hash", e);
        }
    }


    @SuppressWarnings("unused")
    public String generateSalt() throws Exception {
        try {
            SecureRandom random = SecureRandom.getInstance(RANDOM_ALGORITHM);
            byte[] salt = new byte[SALT_LENGTH];
            random.nextBytes(salt);
            return toHex(salt);
        } catch (Exception e) {
            throw new Exception("Unable to generate salt", e);
        }
    }


    private static byte[] generateIv() throws NoSuchAlgorithmException, NoSuchProviderException {
        SecureRandom random = SecureRandom.getInstance(RANDOM_ALGORITHM);
        byte[] iv = new byte[IV_LENGTH];
        random.nextBytes(iv);
        return iv;
    }

}
