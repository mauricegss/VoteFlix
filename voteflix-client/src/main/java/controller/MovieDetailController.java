package controller;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.text.Text;
import model.Movie;

public class MovieDetailController {

    @FXML private Label titleLabel;
    @FXML private Label directorAndYearLabel;
    @FXML private Label genresLabel;
    @FXML private Label ratingLabel;
    @FXML private Text synopsisText;

    public void setMovie(Movie movie) {
        if (movie != null) {
            titleLabel.setText(movie.getTitulo());
            directorAndYearLabel.setText(String.format("%s (%s)", movie.getDiretor(), movie.getAno()));
            genresLabel.setText("Gêneros: " + movie.getGenerosString());
            ratingLabel.setText(String.format("Nota: %.1f/5.0 (%d avaliações)", movie.getNota(), movie.getQtdAvaliacoes()));
            synopsisText.setText(movie.getSinopse());
        }
    }
}