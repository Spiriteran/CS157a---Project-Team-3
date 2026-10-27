package com.cityrecreation.service;

/**
 * Service registry providing singleton access to the active GeneralUserService.
 * Currently backed by MockGeneralUserService. Once SQL queries are reviewed and
 * approved by the user, this can be seamlessly switched to a JDBC-backed service.
 */
public class ServiceRegistry {

    private static final GeneralUserService GENERAL_USER_SERVICE = new MockGeneralUserService();

    public static GeneralUserService getGeneralUserService() {
        return GENERAL_USER_SERVICE;
    }

    private ServiceRegistry() {
    }
}
