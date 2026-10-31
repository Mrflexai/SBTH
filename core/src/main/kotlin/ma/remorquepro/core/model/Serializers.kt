package ma.remorquepro.core.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.LocalDate

/** Date au format ISO « AAAA-MM-JJ ». */
object LocalDateIsoSerializer : KSerializer<LocalDate> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("ma.remorquepro.LocalDate", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: LocalDate) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): LocalDate = LocalDate.parse(decoder.decodeString())
}

/** Date optionnelle : chaîne vide = pas de date. */
object LocalDateIsoNullableSerializer : KSerializer<LocalDate?> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("ma.remorquepro.LocalDateOrEmpty", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: LocalDate?) = encoder.encodeString(value?.toString() ?: "")
    override fun deserialize(decoder: Decoder): LocalDate? =
        decoder.decodeString().takeIf { it.isNotBlank() }?.let(LocalDate::parse)
}
