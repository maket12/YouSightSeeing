package uc_errors

type WrappedError struct {
	Public error
	Reason error
}

func (e *WrappedError) Error() string {
	return e.Public.Error()
}

func (e *WrappedError) Unwrap() error {
	return e.Public
}

func Wrap(public, reason error) error {
	return &WrappedError{
		Public: public,
		Reason: reason,
	}
}
