package th.co.octagon.interactive.ocr_passport.model

import com.google.gson.annotations.SerializedName

data class PassportModel(
    @SerializedName(value = "document_number")
    val documentNumber: String,

    @SerializedName(value = "birth_date")
    val birthDate: Int,

    @SerializedName(value = "expiry_date")
    val expiryDate: Int
)
