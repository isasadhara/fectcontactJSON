package id.ionbit.fectcontactjson

import android.app.Application
import android.content.ContentResolver
import android.database.Cursor
import android.provider.ContactsContract
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

class ContactsViewModel(val mApplication: Application) : AndroidViewModel(mApplication) {

    private var name: String? = null
    private var designation: String? = null
    private var companyName: String? = null
    private val _contactsLiveData = MutableLiveData<ArrayList<Contact>>()
    val contactsLiveData:LiveData<ArrayList<Contact>> = _contactsLiveData

    fun fetchContacts() {

        //buat tampilan di layoutnya
        viewModelScope.launch {
            val contactsListAsync = async { getPhoneContacts() }
            val contactNumbersAsync = async { getContactNumbers() }

            val contacts = contactsListAsync.await()
            val contactNumbers = contactNumbersAsync.await()

            contacts.forEach {
                contactNumbers[it.id]?.let { numbers ->
                    it.numbers = numbers
                }
            }
            _contactsLiveData.postValue(contacts)
        }

        //buat json cek di log
        getContacts()
    }

    private fun getPhoneContacts(): ArrayList<Contact> {
        val contactsList = ArrayList<Contact>()
        val contactsCursor = mApplication.contentResolver?.query(
                ContactsContract.Contacts.CONTENT_URI,
                null,
                null,
                null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC")
        if (contactsCursor != null && contactsCursor.count > 0) {
            val idIndex = contactsCursor.getColumnIndex(ContactsContract.Contacts._ID)
            val nameIndex = contactsCursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
            while (contactsCursor.moveToNext()) {
                val id = contactsCursor.getString(idIndex)
                val name = contactsCursor.getString(nameIndex)
                if (name != null) {
                    contactsList.add(Contact(id, name))
                }
            }
            contactsCursor.close()
        }
        return contactsList
    }

    private fun getContactNumbers(): HashMap<String, ArrayList<String>> {
        val contactsNumberMap = HashMap<String, ArrayList<String>>()
        val phoneCursor: Cursor? = mApplication.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                null,
                null,
                null,
                null
        )
        if (phoneCursor != null && phoneCursor.count > 0) {
            val contactIdIndex = phoneCursor!!.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
            val numberIndex = phoneCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (phoneCursor.moveToNext()) {
                val contactId = phoneCursor.getString(contactIdIndex)
                val number: String = phoneCursor.getString(numberIndex)
                //check if the map contains key or not, if not then create a new array list with number
                if (contactsNumberMap.containsKey(contactId)) {
                    contactsNumberMap[contactId]?.add(number)
                } else {
                    contactsNumberMap[contactId] = arrayListOf(number)
                }
            }
            //contact contains all the number of a particular contact
            phoneCursor.close()
        }
        return contactsNumberMap
    }

    //buat get json nya liat di logcat
    private fun getContacts(): JsonArray {

        val resolver: ContentResolver = mApplication.contentResolver
        val cursor = resolver.query(
                ContactsContract.Contacts.CONTENT_URI, null, null, null,
                null
        )
        val mainJsonArray: JsonArray = JsonArray()
        if (cursor!!.count > 0) {
            while (cursor.moveToNext()) {
                val personJsonObj: JsonObject = JsonObject()
                val id = cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts._ID))
                name = cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME))
                //val phoneNumber = (cursor.getString( cursor.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER) )).toInt()
                personJsonObj.addProperty("NAME", name?.replace("\\", "\\\\")?.replace("'", "\\'")?.replace("\"", "\\\""))

                val orgCursor = resolver.query(
                        ContactsContract.Data.CONTENT_URI, null,
                        ContactsContract.Data.CONTACT_ID + "=?", arrayOf(id), null
                )

                val phoneJsonArray = JsonArray()
                val emailJsonArray = JsonArray()

                if (orgCursor!!.count > 0) {
                    while (orgCursor.moveToNext()) {
                        when {
                            orgCursor.getString(orgCursor.getColumnIndex(ContactsContract.Data.MIMETYPE)) == ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE -> {
                                companyName = orgCursor.getString(orgCursor.getColumnIndex(ContactsContract.Data.DATA1))
                                designation = orgCursor.getString(orgCursor.getColumnIndex(ContactsContract.Data.DATA4))
                                personJsonObj.addProperty(
                                        "ORGANIZATION",
                                        companyName?.replace("\\", "\\\\")?.replace("'", "\\'")?.replace("\"", "\\\"")
                                )
                                personJsonObj.addProperty(
                                        "DESIGNATION",
                                        designation?.replace("\\", "\\\\")?.replace("'", "\\'")?.replace("\"", "\\\"")
                                )

                            }
                            orgCursor.getString(orgCursor.getColumnIndex(ContactsContract.Data.MIMETYPE)).equals(
                                    ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE
                            ) -> {
                                val phoneNum = orgCursor.getString(orgCursor.getColumnIndex(ContactsContract.Data.DATA1))
                                phoneJsonArray.add(phoneNum.replace(" ", "").replace("-", ""))
                            }
                            orgCursor.getString(orgCursor.getColumnIndex(ContactsContract.Data.MIMETYPE)).equals(
                                    ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE
                            ) -> {
                                val emailAddr = orgCursor.getString(orgCursor.getColumnIndex(ContactsContract.Data.DATA1))
                                emailJsonArray.add(emailAddr.replace(" ", ""))
                            }
                        }
                    }
                }
                orgCursor.close()
                personJsonObj.add("EMAIL_LIST", emailJsonArray)
                personJsonObj.add("PHONE_NUMBERS", phoneJsonArray)

                Log.d("jsonlengkap", "$mainJsonArray")
                Log.d("phonename", "$phoneJsonArray $name")

                mainJsonArray.add(personJsonObj)
            }

        }
        cursor.close()
        return mainJsonArray
    }

}