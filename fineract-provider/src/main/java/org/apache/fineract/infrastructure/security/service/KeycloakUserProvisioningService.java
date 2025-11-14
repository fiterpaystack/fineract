/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.fineract.infrastructure.security.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.security.domain.PlatformUser;
import org.apache.fineract.organisation.office.domain.Office;
import org.apache.fineract.organisation.office.domain.OfficeRepository;
import org.apache.fineract.organisation.office.exception.OfficeNotFoundException;
import org.apache.fineract.useradministration.domain.AppUser;
import org.apache.fineract.useradministration.domain.AppUserRepository;
import org.apache.fineract.useradministration.domain.Permission;
import org.apache.fineract.useradministration.domain.PermissionRepository;
import org.apache.fineract.useradministration.domain.Role;
import org.apache.fineract.useradministration.domain.RoleRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service to provision Fineract users from Keycloak JWT tokens.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class KeycloakUserProvisioningService {

    private static final String PAYSTACK_STAFF_ROLE = "Paystack Staff";
    private static final Long DEFAULT_OFFICE_ID = 1L;

    private final AppUserRepository appUserRepository;
    private final OfficeRepository officeRepository;
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;

    /**
     * Find or create a user from Keycloak JWT claims.
     *
     * @param jwt
     *            The JWT token from Keycloak
     * @return PlatformUser (AppUser) for authentication
     */
    @Transactional
    public PlatformUser findOrCreateUser(Jwt jwt) {
        String preferredUsername = jwt.getClaimAsString("preferred_username");
        String email = jwt.getClaimAsString("email");
        String givenName = jwt.getClaimAsString("given_name");
        String familyName = jwt.getClaimAsString("family_name");

        log.debug("Attempting to find or create user from Keycloak JWT. preferred_username: {}, email: {}", preferredUsername, email);

        // Step 1: Search by preferred_username first
        PlatformUser user;
        if (StringUtils.isNotBlank(preferredUsername)) {
            user = appUserRepository.findByUsernameAndDeletedAndEnabled(preferredUsername, false, true);
            if (user != null) {
                log.debug("Found existing user by preferred_username: {}", preferredUsername);
                return user;
            }
        }

        // Step 2: Search by email
        if (StringUtils.isNotBlank(email)) {
            user = appUserRepository.findByEmailAndDeletedAndEnabled(email, false, true);
            if (user != null) {
                log.debug("Found existing user by email: {}", email);
                return user;
            }
        }

        // Step 3: Auto-create user
        log.info("User not found. Auto-provisioning new user from Keycloak. preferred_username: {}, email: {}", preferredUsername, email);
        return createUserFromKeycloak(preferredUsername, email, givenName, familyName);
    }

    private AppUser createUserFromKeycloak(String preferredUsername, String email, String givenName, String familyName) {
        // Validate required fields
        if (StringUtils.isBlank(preferredUsername)) {
            throw new IllegalArgumentException("preferred_username claim is required to provision user");
        }
        if (StringUtils.isBlank(email)) {
            throw new IllegalArgumentException("email claim is required to provision user");
        }

        // Generate random password (user won't use it, Keycloak handles auth)
        String randomPassword = new RandomPasswordGenerator(16).generate();

        // Get or create default office
        Office office = officeRepository.findById(DEFAULT_OFFICE_ID).orElseThrow(() -> new OfficeNotFoundException(DEFAULT_OFFICE_ID));

        // Get or create "Paystack Staff" role
        Role paystackStaffRole = getOrCreatePaystackStaffRole();

        Set<Role> roles = new HashSet<>();
        roles.add(paystackStaffRole);

        // Create Spring Security User for AppUser constructor
        Collection<SimpleGrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("DUMMY_ROLE_NOT_USED_OR_PERSISTED_TO_AVOID_EXCEPTION"));

        User user = new User(preferredUsername, randomPassword, true, // enabled
                true, // accountNonExpired
                true, // credentialsNonExpired
                true, // accountNonLocked
                authorities);

        // Set default names if not provided
        String firstName = StringUtils.isNotBlank(givenName) ? givenName : preferredUsername;
        String lastName = StringUtils.isNotBlank(familyName) ? familyName : "";

        // Create AppUser
        AppUser appUser = new AppUser(office, user, roles, email, firstName, lastName, null, // staff (not linked)
                true, // passwordNeverExpires (Keycloak manages auth)
                false, // isSelfServiceUser (back-office user)
                null, // clients (not a self-service user)
                true // cannotChangePassword (Keycloak manages password)
        );

        // Save and return
        appUser = appUserRepository.saveAndFlush(appUser);
        log.info("Successfully auto-provisioned user from Keycloak. userId: {}, username: {}, email: {}", appUser.getId(),
                appUser.getUsername(), appUser.getEmail());

        return appUser;
    }

    private Role getOrCreatePaystackStaffRole() {
        Role role = roleRepository.getRoleByName(PAYSTACK_STAFF_ROLE);

        if (role != null) {
            log.debug("Found existing '{}' role", PAYSTACK_STAFF_ROLE);
            return role;
        }

        // Create the role with all READ permissions
        log.info("Creating '{}' role with all READ permissions", PAYSTACK_STAFF_ROLE);

        role = new Role(PAYSTACK_STAFF_ROLE, "Auto-created role for Keycloak SSO users with read-only access");

        // Get all READ permissions
        List<Permission> allPermissions = permissionRepository.findAll();
        Set<Permission> readPermissions = new HashSet<>();

        for (Permission permission : allPermissions) {
            // Include permissions that are READ operations
            if (permission.getCode() != null
                    && (permission.getCode().startsWith("READ_") || permission.getCode().equals("ALL_FUNCTIONS_READ"))) {
                readPermissions.add(permission);
                log.debug("Adding READ permission to role: {}", permission.getCode());
            }
        }

        // Add the permissions to the role
        for (Permission permission : readPermissions) {
            role.updatePermission(permission, true);
        }

        role = roleRepository.saveAndFlush(role);
        log.info("Successfully created '{}' role with {} READ permissions", PAYSTACK_STAFF_ROLE, readPermissions.size());

        return role;
    }
}
