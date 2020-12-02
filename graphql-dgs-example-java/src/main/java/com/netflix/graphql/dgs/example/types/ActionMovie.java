package com.netflix.graphql.dgs.example.types;

public class ActionMovie implements Movie {
  private String title;

  private String director;

  private int nrOfExplosions;

  public ActionMovie() {
  }

  public ActionMovie(String title, String director, int nrOfExplosions) {
    this.title = title;
    this.director = director;
    this.nrOfExplosions = nrOfExplosions;
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

  public int getNrOfExplosions() {
    return nrOfExplosions;
  }

  public void setNrOfExplosions(int nrOfExplosions) {
    this.nrOfExplosions = nrOfExplosions;
  }

  @Override
  public String toString() {
    return "ActionMovie{" + "title='" + title + "', " +"director='" + director + "', " +"nrOfExplosions='" + nrOfExplosions + "' " +"}";
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ActionMovie that = (ActionMovie) o;
        return java.util.Objects.equals(title, that.title) &&
                            java.util.Objects.equals(director, that.director) &&
                            nrOfExplosions == that.nrOfExplosions;
  }

  @Override
  public int hashCode() {
    return java.util.Objects.hash(title, director, nrOfExplosions);
  }
}
