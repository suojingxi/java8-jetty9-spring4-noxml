package myapp.controller;

public class MyData {

    private Integer number;
    private String word;

    public MyData() {
    }

    public MyData( Integer number, String word ) {
        this.number = number;
        this.word = word;
    }

    public Integer getNumber() {
        return number;
    }

    public void setNumber( Integer number ) {
        this.number = number;
    }

    public String getWord() {
        return word;
    }

    public void setWord( String word ) {
        this.word = word;
    }

}
