package br.com.familhas.crm;

import java.util.List;

/** Quem está logado no CRM e o que pode fazer: o Angular usa só para UX (menu, botões); a API sempre confere. */
public record CrmMeResponse(String subject, String username, String name, String email, List<String> roles,
        List<String> permissions) {
}
