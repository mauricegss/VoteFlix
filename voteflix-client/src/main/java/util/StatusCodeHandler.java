package util;

import java.util.Map;
import java.util.HashMap;

public class StatusCodeHandler {

    private static final Map<String, String> statusMessages = new HashMap<>();

    static {
        // 2xx Sucesso
        statusMessages.put("200", "Operação realizada com sucesso.");
        statusMessages.put("201", "Recurso criado com sucesso.");

        // 4xx Erros do Cliente
        statusMessages.put("400", "Requisição inválida. Verifique os dados enviados.");
        statusMessages.put("401", "Credenciais inválidas ou token expirado. Faça o login novamente.");
        statusMessages.put("403", "Você não tem permissão para acessar este recurso.");
        statusMessages.put("404", "O recurso solicitado não foi encontrado.");
        statusMessages.put("409", "O usuário ou recurso que você está tentando criar já existe.");
        statusMessages.put("422", "Dados inválidos ou faltando. Verifique o preenchimento dos campos.");

        // 5xx Erros do Servidor
        statusMessages.put("500", "Ocorreu um erro inesperado no servidor. Por favor, tente novamente mais tarde.");

        // 9xx Erros customizados do cliente
        statusMessages.put("999", "Não foi possível conectar ao servidor. Verifique o IP, a porta e sua conexão.");
    }

    /**
     * Retorna uma mensagem padrão para um determinado código de status.
     * @param statusCode O código de status (ex: "404").
     * @return A mensagem correspondente ou uma mensagem de erro genérica.
     */
    public static String getMessage(String statusCode) {
        return statusMessages.getOrDefault(statusCode, "Ocorreu um erro desconhecido (código: " + statusCode + ").");
    }
}