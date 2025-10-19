package model;

import org.json.JSONObject;
import java.io.Serializable;

public class User implements Serializable {

    private int id;
    private String nome;
    private String senha;

    public User() {
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getSenha() {
        return senha;
    }

    public void setSenha(String senha) {
        this.senha = senha;
    }

    public static User fromJson(JSONObject json) {
        User user = new User();
        user.setId(Integer.parseInt(json.getString("id")));
        user.setNome(json.getString("nome"));
        return user;
    }
}