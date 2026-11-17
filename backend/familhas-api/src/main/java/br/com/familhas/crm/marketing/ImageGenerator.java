package br.com.familhas.crm.marketing;

/** Gera uma imagem a partir do prompt. Hoje só a OpenAI; sem provedor, o registry barra antes (503). */
public interface ImageGenerator {

    /** Bytes da imagem (JPEG). Recusa do provedor: 422 AI_IMAGE_REFUSED; falha: 502 AI_IMAGE_FAILED. */
    byte[] generate(String prompt);
}
