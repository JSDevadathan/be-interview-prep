package com.edstem.interviewprep.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class AdminAccountPropertiesTest {

    @Test
    void usernameWithoutPasswordIsRejected() {
        assertThatThrownBy(() -> new AdminAccountProperties("admin", ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("app.security.admin.username and app.security.admin.password must be set together");
    }

    @Test
    void blankUsernameAndPasswordMeanNoAdminIsConfigured() {
        assertThat(new AdminAccountProperties("", "").isConfigured()).isFalse();
    }

    @Test
    void usernameAndPasswordTogetherMeanAdminIsConfigured() {
        assertThat(new AdminAccountProperties("admin", "secret-password").isConfigured()).isTrue();
    }
}
