stopifnot(require(testrffi))

rffi.addInt(2L, 3L)
rffi.addDouble(2, 3)
rffi.populateIntVector(5)
rffi.populateLogicalVector(5)
rffi.mkStringFromChar()
rffi.mkStringFromBytes()
rffi.null()
try(rffi.null.E())
rffi.null.C()
rffi.isRString(character(0))
a <- c(1L,2L,3L); rffi.iterate_iarray(a)
a <- c(1L,2L,3L); rffi.iterate_iptr(a)
rffi.dotCModifiedArguments(c(0,1,2,3))
rffi.dotExternalAccessArgs(1L, 3, c(1,2,3), c('a', 'b'), 'b', TRUE, as.raw(12))
rffi.dotExternalAccessArgs(x=1L, 3, c(1,2,3), y=c('a', 'b'), 'b', TRUE, as.raw(12))
rffi.invoke12()
rffi.TYPEOF(3L)
rffi.isRString("hello")
rffi.isRString(NULL)
rffi.interactive()
x <- 1; rffi.findvar("x", globalenv())
x <- "12345"; rffi.char_length(x)

strVec <- rffi.getStringNA();
stopifnot(anyNA(strVec))

x <- list(1)
attribute(x, 'myattr') <- 'hello';
attrs <- ATTRIB(x)
stopifnot(attrs[[1]] == 'hello')

# loess invokes loess_raw native function passing in string value as argument and that is what we test here.
loess(dist ~ speed, cars);
