package br.com.familhas.crm.common;

/**
 * CPF: só dígitos no banco, validado pelos dígitos verificadores, e mascarado em toda resposta da API
 * ("***.456.789-**"), porque a tela quase nunca precisa do número inteiro (LGPD, minimização).
 */
public final class Cpf {

    private Cpf() {
    }

    /** Só os dígitos, ou null para vazio. Não valida. */
    public static String digits(String cpf) {
        if (cpf == null) {
            return null;
        }
        String digits = cpf.replaceAll("\\D", "");
        return digits.isEmpty() ? null : digits;
    }

    public static boolean isValid(String digits) {
        if (digits == null || !digits.matches("\\d{11}") || digits.chars().distinct().count() == 1) {
            return false;
        }
        return checkDigit(digits, 9) == digits.charAt(9) - '0' && checkDigit(digits, 10) == digits.charAt(10) - '0';
    }

    /** "***.456.789-**": mostra só o meio, o bastante para conferir com o documento do cliente. */
    public static String mask(String digits) {
        if (digits == null || digits.length() != 11) {
            return null;
        }
        return "***." + digits.substring(3, 6) + "." + digits.substring(6, 9) + "-**";
    }

    private static int checkDigit(String digits, int length) {
        int sum = 0;
        for (int i = 0; i < length; i++) {
            sum += (digits.charAt(i) - '0') * (length + 1 - i);
        }
        int rest = (sum * 10) % 11;
        return rest == 10 ? 0 : rest;
    }
}
