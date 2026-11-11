package app.daranotes.desktop

import com.github.javakeyring.Keyring
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference

/** Passwords in the system's secret store, by service and account. */
interface Secrets {
    fun get(service: String, account: String): String?
    fun set(service: String, account: String, value: String)
    fun delete(service: String, account: String)

    companion object {
        /**
         * The macOS Keychain directly through JNA on macOS (java-keyring's macOS helper is an
         * unsigned native library, which notarization rejects); java-keyring elsewhere
         * (Windows Credential Manager, the Secret Service on Linux).
         */
        fun system(): Secrets =
            if ("mac" in System.getProperty("os.name").lowercase()) MacKeychain else JavaKeyring
    }
}

/** The login keychain, through Security.framework's generic-password calls. */
private object MacKeychain : Secrets {
    @Suppress("FunctionName")
    private interface Security : Library {
        fun SecKeychainFindGenericPassword(
            keychainOrArray: Pointer?, serviceNameLength: Int, serviceName: ByteArray,
            accountNameLength: Int, accountName: ByteArray,
            passwordLength: IntByReference?, passwordData: PointerByReference?, itemRef: PointerByReference?,
        ): Int

        fun SecKeychainAddGenericPassword(
            keychain: Pointer?, serviceNameLength: Int, serviceName: ByteArray,
            accountNameLength: Int, accountName: ByteArray,
            passwordLength: Int, passwordData: ByteArray, itemRef: Pointer?,
        ): Int

        fun SecKeychainItemModifyAttributesAndData(itemRef: Pointer, attrList: Pointer?, length: Int, data: ByteArray): Int
        fun SecKeychainItemDelete(itemRef: Pointer): Int
        fun SecKeychainItemFreeContent(attrList: Pointer?, data: Pointer?): Int
    }

    private interface CoreFoundation : Library {
        fun CFRelease(cf: Pointer)
    }

    private const val ERR_SEC_ITEM_NOT_FOUND = -25300

    private val security by lazy { Native.load("Security", Security::class.java) }
    private val cf by lazy { Native.load("CoreFoundation", CoreFoundation::class.java) }

    /** Finds the item; returns (status, item, password), the item to be released by the caller. */
    private fun find(service: String, account: String, wantPassword: Boolean): Triple<Int, Pointer?, String?> {
        val s = service.toByteArray()
        val a = account.toByteArray()
        val length = IntByReference()
        val data = PointerByReference()
        val item = PointerByReference()
        val status = security.SecKeychainFindGenericPassword(
            null, s.size, s, a.size, a,
            if (wantPassword) length else null, if (wantPassword) data else null, item,
        )
        if (status != 0) return Triple(status, null, null)
        val password = if (wantPassword) {
            data.value?.getByteArray(0, length.value)?.toString(Charsets.UTF_8)
                .also { security.SecKeychainItemFreeContent(null, data.value) }
        } else null
        return Triple(0, item.value, password)
    }

    override fun get(service: String, account: String): String? {
        val (status, item, password) = find(service, account, wantPassword = true)
        item?.let(cf::CFRelease)
        if (status != 0 && status != ERR_SEC_ITEM_NOT_FOUND) error("Keychain error $status")
        return password
    }

    override fun set(service: String, account: String, value: String) {
        val bytes = value.toByteArray()
        val (status, item, _) = find(service, account, wantPassword = false)
        val result = if (item != null) {
            security.SecKeychainItemModifyAttributesAndData(item, null, bytes.size, bytes).also { cf.CFRelease(item) }
        } else if (status == ERR_SEC_ITEM_NOT_FOUND) {
            val s = service.toByteArray()
            val a = account.toByteArray()
            security.SecKeychainAddGenericPassword(null, s.size, s, a.size, a, bytes.size, bytes, null)
        } else status
        if (result != 0) error("Keychain error $result")
    }

    override fun delete(service: String, account: String) {
        val (_, item, _) = find(service, account, wantPassword = false)
        item?.let {
            security.SecKeychainItemDelete(it)
            cf.CFRelease(it)
        }
    }
}

/** java-keyring: Windows Credential Manager, or the Secret Service on Linux. */
private object JavaKeyring : Secrets {
    override fun get(service: String, account: String): String? =
        Keyring.create().use { k -> runCatching { k.getPassword(service, account) }.getOrNull() }

    override fun set(service: String, account: String, value: String) =
        Keyring.create().use { k -> k.setPassword(service, account, value) }

    override fun delete(service: String, account: String) =
        Keyring.create().use { k -> runCatching { k.deletePassword(service, account) }; Unit }
}
