package model;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class Movie implements Serializable {

    private int id;
    private String titulo;
    private String diretor;
    private String ano;
    private List<String> generos;
    private String sinopse;
    private double nota;
    private int qtdAvaliacoes;

    public Movie() {
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getTitulo() {
        return titulo;
    }

    public void setTitulo(String titulo) {
        this.titulo = titulo;
    }

    public String getDiretor() {
        return diretor;
    }

    public void setDiretor(String diretor) {
        this.diretor = diretor;
    }

    public String getAno() {
        return ano;
    }

    public void setAno(String ano) {
        this.ano = ano;
    }

    public List<String> getGeneros() {
        return generos;
    }

    public void setGeneros(List<String> generos) {
        this.generos = generos;
    }

    public String getSinopse() {
        return sinopse;
    }

    public void setSinopse(String sinopse) {
        this.sinopse = sinopse;
    }

    public double getNota() {
        return nota;
    }

    public void setNota(double nota) {
        this.nota = nota;
    }

    public int getQtdAvaliacoes() {
        return qtdAvaliacoes;
    }

    public void setQtdAvaliacoes(int qtdAvaliacoes) {
        this.qtdAvaliacoes = qtdAvaliacoes;
    }

    public String getGenerosString() {
        if (generos == null || generos.isEmpty()) {
            return "";
        }
        return String.join(", ", generos);
    }

    public static Movie fromJson(JSONObject json) {
        Movie movie = new Movie();
        movie.setId(Integer.parseInt(json.getString("id")));
        movie.setTitulo(json.getString("titulo"));
        movie.setDiretor(json.getString("diretor"));
        movie.setAno(json.getString("ano"));
        movie.setSinopse(json.getString("sinopse"));
        movie.setNota(Double.parseDouble(json.getString("nota").replace(",", ".")));
        movie.setQtdAvaliacoes(Integer.parseInt(json.getString("qtd_avaliacoes")));

        JSONArray generosJson = json.getJSONArray("genero");
        List<String> generos = new ArrayList<>();
        for (int i = 0; i < generosJson.length(); i++) {
            generos.add(generosJson.getString(i));
        }
        movie.setGeneros(generos);

        return movie;
    }
}