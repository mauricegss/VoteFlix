package model;

import java.io.Serializable;

public class Review implements Serializable {

    private int id;
    private int idFilme;
    private int idUsuario;
    private String nomeUsuario;
    private String titulo;
    private String descricao;
    private int nota;
    private String data;

    // Novo campo para persistência
    private String editado;

    // Campo auxiliar de UI (não persistido no banco, mas enviado pelo JSON)
    private boolean isOwnReview;

    public Review() {
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getIdFilme() {
        return idFilme;
    }

    public void setIdFilme(int idFilme) {
        this.idFilme = idFilme;
    }

    public int getIdUsuario() {
        return idUsuario;
    }

    public void setIdUsuario(int idUsuario) {
        this.idUsuario = idUsuario;
    }

    public String getNomeUsuario() {
        return nomeUsuario;
    }

    public void setNomeUsuario(String nomeUsuario) {
        this.nomeUsuario = nomeUsuario;
    }

    public String getTitulo() {
        return titulo;
    }

    public void setTitulo(String titulo) {
        this.titulo = titulo;
    }

    public String getDescricao() {
        return descricao;
    }

    public void setDescricao(String descricao) {
        this.descricao = descricao;
    }

    public int getNota() {
        return nota;
    }

    public void setNota(int nota) {
        this.nota = nota;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }

    // --- Novos Getters e Setters (Resolvem o "cannot find symbol") ---

    public String getEditado() {
        return editado;
    }

    public void setEditado(String editado) {
        this.editado = editado;
    }

    public boolean isOwnReview() {
        return isOwnReview;
    }

    public void setOwnReview(boolean ownReview) {
        isOwnReview = ownReview;
    }
}