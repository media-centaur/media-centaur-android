package app.mediacentaur.tv.bridge

import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceDeclarationTest {
    @Test
    fun declaresTheModelOsAppVersionAndFormFactor() {
        val headers = DeviceDeclaration.headers("Maker", "Sample TV 4K", "12", "0.3.0", DeviceDeclaration.FormFactor.TV)
        assertEquals(
            mapOf(
                "x-media-centaur-device-model" to "Maker Sample TV 4K",
                "x-media-centaur-device-os" to "Android 12",
                "x-media-centaur-app-version" to "0.3.0",
                "x-media-centaur-form-factor" to "tv",
            ),
            headers,
        )
    }

    @Test
    fun aModelThatAlreadyNamesItsMakerIsNotPrefixedTwice() {
        val headers = DeviceDeclaration.headers("sample", "Sample Phone 9", "14", "0.3.0", DeviceDeclaration.FormFactor.PHONE)
        assertEquals("Sample Phone 9", headers["x-media-centaur-device-model"])
        assertEquals("phone", headers["x-media-centaur-form-factor"])
    }

    @Test
    fun aValueAHeaderCannotCarryIsReducedToWhatItCan() {
        val headers = DeviceDeclaration.headers("Sämple", "TV 4K", "12", "0.3.0", DeviceDeclaration.FormFactor.TABLET)
        assertEquals("Smple TV4K", headers["x-media-centaur-device-model"])
        assertEquals("tablet", headers["x-media-centaur-form-factor"])
    }
}
