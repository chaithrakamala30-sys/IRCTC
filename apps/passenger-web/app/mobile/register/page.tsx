import { PassengerIdentityExperience } from "../../../components/identity-experience";

export const metadata = { title: "Create account — Mobile passenger" };

export default function MobileRegisterPage() {
  return <PassengerIdentityExperience device="mobile" mode="register" />;
}
