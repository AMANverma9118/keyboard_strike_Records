package com.keywordrecord.keyboard

object ChatContextHolder {
    @Volatile
    var contactName: String = ""

    @Volatile
    var appPackage: String = ""

    fun update(contact: String, packageName: String) {
        contactName = contact.trim()
        appPackage = packageName.trim()
    }

    fun clear() {
        contactName = ""
        appPackage = ""
    }
}
