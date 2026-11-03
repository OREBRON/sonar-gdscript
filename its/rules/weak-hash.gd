func f(ctx):
	ctx.start(HashingContext.HASH_MD5)  # Noncompliant
	ctx.start(HashingContext.HASH_SHA1)  # Noncompliant
	ctx.start(HashingContext.HASH_SHA256)
