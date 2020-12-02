package com.netflix.graphql.dgs.example.types;

public class ScaryMovie implements Movie {
  private String title;

  private String director;

  private boolean gory;

  private int scareFactor;

  public ScaryMovie() {
  }

  public ScaryMovie(String title, String director, boolean gory, int scareFactor) {
    this.title = title;
    this.director = director;
    this.gory = gory;
    this.scareFactor = scareFactor;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getDirector() {
    return director;
  }

  public void setDirector(String director) {
    this.director = director;
  }

  public boolean getGory() {
    return gory;
  }

  public void setGory(boolean gory) {
    this.gory = gory;
  }

  public int getScareFactor() {
    return scareFactor;
  }

  public void setScareFactor(int scareFactor) {
    this.scareFactor = scareFactor;
  }

  @Override
  public String toString() {
    return "ScaryMovie{" + "title='" + title + "', " +"director='" + director + "', " +"gory='" + gory + "', " +"scareFactor='" + scareFactor + "' " +"}";
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ScaryMovie that = (ScaryMovie) o;
        return java.util.Objects.equals(title, that.title) &&
                            java.util.Objects.equals(director, that.director) &&
                            gory == that.gory &&
                            scareFactor == that.scareFactor;
  }

  @Override
  public int hashCode() {
    return java.util.Objects.hash(title, director, gory, scareFactor);
  }
}
