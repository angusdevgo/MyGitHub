/**
 * 端侧加密工具库 (基于 Web Crypto API 原生 AES-GCM 与 SHA-256 + 紧凑字节转码)
 */

export class CryptoHelper {
  static bufferToBase64(buffer) {
    const bytes = new Uint8Array(buffer);
    let binary = '';
    for (let i = 0; i < bytes.byteLength; i++) {
      binary += String.fromCharCode(bytes[i]);
    }
    return btoa(binary);
  }

  static base64ToBuffer(base64) {
    const binary = atob(base64);
    const bytes = new Uint8Array(binary.length);
    for (let i = 0; i < binary.length; i++) {
      bytes[i] = binary.charCodeAt(i);
    }
    return bytes.buffer;
  }

  static bufferToHex(buffer) {
    return Array.from(new Uint8Array(buffer))
      .map(b => b.toString(16).padStart(2, '0'))
      .join('');
  }

  static hexToBuffer(hex) {
    const bytes = new Uint8Array(hex.length / 2);
    for (let i = 0; i < bytes.length; i++) {
      bytes[i] = parseInt(hex.substr(i * 2, 2), 16);
    }
    return bytes.buffer;
  }

  /**
   * 生成 AES-256 共享密钥 (从共享口令或 ECDH 协商)
   */
  static async deriveKey(secretStr) {
    const enc = new TextEncoder();
    const keyMaterial = await crypto.subtle.importKey(
      "raw",
      enc.encode(secretStr),
      { name: "PBKDF2" },
      false,
      ["deriveKey"]
    );

    return crypto.subtle.deriveKey(
      {
        name: "PBKDF2",
        salt: enc.encode("MyGitHub-Relay-Salt"),
        iterations: 10000,
        hash: "SHA-256"
      },
      keyMaterial,
      { name: "AES-GCM", length: 256 },
      false,
      ["encrypt", "decrypt"]
    );
  }

  /**
   * AES-GCM 加密明文字符串
   */
  static async encrypt(plainText, secretStr) {
    const key = await this.deriveKey(secretStr);
    const iv = crypto.getRandomValues(new Uint8Array(12));
    const enc = new TextEncoder();
    const cipherBuffer = await crypto.subtle.encrypt(
      { name: "AES-GCM", iv },
      key,
      enc.encode(plainText)
    );

    return {
      ciphertext: this.bufferToBase64(cipherBuffer),
      nonce: this.bufferToBase64(iv)
    };
  }

  /**
   * AES-GCM 解密密文字符串
   */
  static async decrypt(ciphertextBase64, nonceBase64, secretStr) {
    const key = await this.deriveKey(secretStr);
    const iv = new Uint8Array(this.base64ToBuffer(nonceBase64));
    const cipherBuffer = this.base64ToBuffer(ciphertextBase64);

    const decryptedBuffer = await crypto.subtle.decrypt(
      { name: "AES-GCM", iv },
      key,
      cipherBuffer
    );

    const dec = new TextDecoder();
    return dec.decode(decryptedBuffer);
  }

  /**
   * 生成设备公私钥指纹与标识 (DID)
   */
  static async generateDeviceDID() {
    const randomBytes = crypto.getRandomValues(new Uint8Array(16));
    const hex = this.bufferToHex(randomBytes);
    return `did:mygithub:pc:${hex.substring(0, 12)}`;
  }
}
