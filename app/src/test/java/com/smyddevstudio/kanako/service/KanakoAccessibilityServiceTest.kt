package com.smyddevstudio.kanako.service

import android.text.InputType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KanakoAccessibilityServiceTest {
    @Test
    fun detectsPasswordInputTypesWithoutTreatingOrdinaryFieldsAsSensitive() {
        assertTrue(
            isSensitiveInputType(
                isPassword = false,
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            )
        )
        assertTrue(
            isSensitiveInputType(
                isPassword = false,
                inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            )
        )
        assertTrue(isSensitiveInputType(isPassword = true, inputType = 0))
        assertFalse(
            isSensitiveInputType(
                isPassword = false,
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            )
        )
    }
    @Test
    fun ordinaryUriAndNumberFieldsAreNotPasswords() {
        assertFalse(isSensitiveInputType(
            false, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        ))
        assertFalse(isSensitiveInputType(false, InputType.TYPE_CLASS_NUMBER))
        assertTrue(isSensitiveInputType(
            false, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
        ))
    }
}
