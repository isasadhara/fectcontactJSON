package id.ionbit.fectcontactjson

data class Contact(val id: String, val name:String) {
    var numbers = ArrayList<String>()
    var emails = ArrayList<String>()
}

//data class Contact(val id: String, val contactNew:List<ContactNew>)