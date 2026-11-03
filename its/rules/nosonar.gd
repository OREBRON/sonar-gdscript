# Every line marked NOSONAR has a finding locally; the scanner must drop it on the server.
const lower_constant = 2  # NOSONAR
func f(unused):  # NOSONAR
	var x = done == true  # NOSONAR
	print(x)
