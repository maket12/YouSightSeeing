package dto

type GetUsersList struct {
	Limit  int `json:"limit"`
	Offset int `json:"offset"`
}
