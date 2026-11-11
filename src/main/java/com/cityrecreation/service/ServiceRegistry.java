package com.cityrecreation.service;

/**
 * Service registry providing singleton access to the active GeneralUserService,
 * backed by JdbcGeneralUserService against the MySQL `city_recreation` database.
 */
public class ServiceRegistry {

    private static final GeneralUserService GENERAL_USER_SERVICE = new JdbcGeneralUserService();

    public static GeneralUserService getGeneralUserService() {
        return GENERAL_USER_SERVICE;
    }

    private ServiceRegistry() {
    }
}
