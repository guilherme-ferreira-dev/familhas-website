package br.com.familhas.crm;

/**
 * Formato das authorities de permissão do CRM: {@code crm:<recurso>:<ação>}, por exemplo
 * {@code crm:vouchers:criar}. Os controllers do CRM usam {@code @PreAuthorize("hasAuthority('crm:vouchers:criar')")};
 * {@code CrmPermissionLiteralsTests} garante que todo literal usado existe.
 */
public final class CrmPermissions {

    public static final String PREFIX = "crm:";

    private CrmPermissions() {
    }

    public static String authority(CrmResource resource, CrmAction action) {
        return PREFIX + resource.key() + ":" + action.key();
    }
}
