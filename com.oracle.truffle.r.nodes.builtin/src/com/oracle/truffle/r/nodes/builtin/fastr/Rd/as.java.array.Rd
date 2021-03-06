% Generated by roxygen2: do not edit by hand
% Please edit documentation in R/fastrInteropt.R
\name{as.java.array}
\alias{as.java.array}
\title{Converts a R vector or list to a java array.}
\usage{
as.java.array(x, className)
}
\arguments{
\item{x}{a vector or list}

\item{className}{Optional. Determines the java array component type.}
}
\value{
An external object representing a java array. Error in case the array could not be created.
}
\description{
Converts a R vector or list to a java array.
}
\examples{
as.java.array(c(1, 2, 3), 'java.lang.Double')
}
\seealso{
\code{\link{new.java.array}}, \code{\link{is.external.array}}
}
