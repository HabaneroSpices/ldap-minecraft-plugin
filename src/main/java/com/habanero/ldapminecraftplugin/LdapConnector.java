package com.habanero.ldapminecraftplugin;

import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.core.AttributesMapper;
import org.springframework.ldap.core.support.LdapContextSource;
import org.springframework.ldap.filter.EqualsFilter;
import org.springframework.ldap.filter.Filter;

import java.util.logging.Level;
import java.util.logging.Logger;

public class LdapConnector {
    private final LdapTemplate ldapTemplate;
    private final String userAttributeName;
    private final Logger logger;

    public LdapConnector(String url, String baseDn, String userDn, String password, String userAttributeName, Logger logger) {
        this.logger = logger;
        this.userAttributeName = userAttributeName;

        LdapContextSource contextSource = new LdapContextSource();
        contextSource.setUrl(url);
        contextSource.setBase(baseDn);
        contextSource.setUserDn(userDn);
        contextSource.setPassword(password);
        contextSource.setPooled(true); // Needs explaining

        try {
            contextSource.afterPropertiesSet();
            this.ldapTemplate = new LdapTemplate(contextSource);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to initialize LDAP connection", e);
            throw new RuntimeException("Failed to initialize LDAP connection", e);
        }
    }

    public boolean usernameExists(String username) {
        try {
            Filter filter = new EqualsFilter(userAttributeName, username);
            AttributesMapper<Object> mapper = attrs -> attrs;
            return !ldapTemplate.search("", filter.encode(), mapper).isEmpty();
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error checking username in LDAP", e);
            return false;
        }
    }
}
