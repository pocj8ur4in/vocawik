package com.vocawik.domain.acl;

/** ACL subject condition type. */
public enum AclSubjectType {
    ANONYMOUS,
    USER,
    USER_15,
    USER_VERIFIED,
    ADMIN,
    USER_ID,
    GUEST_ID,
    ACL_GROUP
}
